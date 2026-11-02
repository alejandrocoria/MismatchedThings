package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.mixin.ServerInteractionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native open/result packets, then a second paid transformation of that same live root. */
final class SmithingClientChecks {
    private static int ticks;
    private static int stage;
    private static int phase;
    private static boolean finished;
    private static volatile boolean ready;
    private static volatile boolean openingReady;
    private static volatile boolean paid;
    private static volatile Throwable failure;
    private static volatile ItemStack expected = ItemStack.EMPTY;

    private SmithingClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated smithing client became visible");
            if (failure != null) {
                throw new AssertionError("Smithing packet server check failed", failure);
            }
            require(++ticks < 1800, "Smithing client timed out at stage " + stage + "/" + phase);
            if (client.player == null || client.level == null || client.gameMode == null || client.getSingleplayerServer() == null) {
                return;
            }
            switch (stage) {
                case 0 -> {
                    if (client.screen != null) {
                        return;
                    }
                    onServer(client, () -> {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.setGameMode(GameType.SURVIVAL);
                        var station = SmithingChecks.STATION;
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) {
                                server.overworld().setBlock(station.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                            }
                        }
                        server.overworld().setBlock(station, Blocks.SMITHING_TABLE.defaultBlockState(), 3);
                        player.connection.teleport(0.5, 65, 2.5, 180, 0);
                        player.getInventory().clearContent();
                        player.inventoryMenu.broadcastChanges();
                    });
                    stage = 1;
                }
                case 1 -> {
                    var station = SmithingChecks.STATION;
                    if (!ready || !client.level.getBlockState(station).is(Blocks.SMITHING_TABLE)
                            || client.player.distanceToSqr(Vec3.atCenterOf(station)) > 16) {
                        return;
                    }
                    if (!openingReady) {
                        onServer(client, () -> {
                            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            openingReady = player.connection.hasClientLoaded()
                                    && ((ServerInteractionAccessor) player.connection).leggings$pendingTeleport() == null;
                        });
                        return;
                    }
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(station), Direction.UP, station, false));
                    stage = 2;
                }
                case 2 -> {
                    if (!(client.player.containerMenu instanceof SmithingMenu)) {
                        return;
                    }
                    onServer(client, () -> {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var root = phase == 0 ? SmithingChecks.decorate(server, MixedLeggingsFactory.createPair(
                                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)).primary()) : expected.copy();
                        var menu = (SmithingMenu) player.containerMenu;
                        menu.getSlot(0).set(new ItemStack(phase == 0 ? Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE : Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
                        menu.getSlot(1).set(root);
                        menu.getSlot(2).set(new ItemStack(phase == 0 ? Items.NETHERITE_INGOT : Items.DIAMOND, 2));
                        expected = menu.getSlot(3).getItem().copy();
                        SmithingChecks.preserved(root, expected);
                        paid = false;
                        menu.broadcastChanges();
                    });
                    stage = 3;
                }
                case 3 -> {
                    if (!ready || !ItemStack.matches(expected, client.player.containerMenu.getSlot(3).getItem())) {
                        return;
                    }
                    client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 3, 0,
                            phase == 0 ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE, client.player);
                    stage = 4;
                }
                case 4 -> {
                    if (!ready) {
                        return;
                    }
                    if (!paid) {
                        onServer(client, () -> {
                            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            var menu = player.containerMenu;
                            boolean received = phase == 0 ? !menu.getCarried().isEmpty() : WorkbenchChecks.contains(player, expected);
                            if (!received) {
                                return;
                            }
                            require((phase != 0 || ItemStack.matches(expected, menu.getCarried())) && !menu.getSlot(1).hasItem()
                                            && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
                                    "Smithing packet changed paid output or consumed more than one addition/template");
                            paid = true;
                        });
                        return;
                    }
                    boolean visible = phase == 0 ? ItemStack.matches(expected, client.player.containerMenu.getCarried())
                            : client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(expected, stack));
                    if (!visible) {
                        return;
                    }
                    if (phase == 1) {
                        SkeletonChecks.passed("client");
                        finished = true;
                        client.stop();
                        return;
                    }
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                        player.closeContainer();
                        // Unused native template/addition returns are not inputs to the next check.
                        player.getInventory().clearContent();
                        player.inventoryMenu.broadcastChanges();
                    });
                    stage = 5;
                }
                case 5 -> {
                    if (!ready || client.player.containerMenu != client.player.inventoryMenu) {
                        return;
                    }
                    phase = 1;
                    openingReady = false;
                    stage = 1;
                }
                default -> throw new AssertionError("Unknown smithing client stage");
            }
        } catch (Throwable error) {
            finished = true;
            SkeletonChecks.failed("client", error);
            client.stop();
        }
    }

    private static void onServer(Minecraft client, Runnable action) {
        ready = false;
        client.getSingleplayerServer().execute(() -> {
            try {
                action.run();
                ready = true;
            } catch (Throwable error) {
                failure = error;
            }
        });
    }
}
