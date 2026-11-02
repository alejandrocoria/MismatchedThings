package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.mixin.ServerInteractionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native screen/open/click packets and synchronized full previews; no custom UI or screenshots. */
final class RepairClientChecks {
    private static final BlockPos ANVIL = new BlockPos(0, 65, 0);
    private static int ticks;
    private static int stage;
    private static int settledAt;
    private static int levels;
    private static int price;
    private static boolean finished;
    private static volatile boolean ready;
    private static volatile boolean openingReady;
    private static volatile boolean anvilPaid;
    private static volatile boolean craftingPaid;
    private static volatile Throwable failure;
    private static volatile ItemStack expected = ItemStack.EMPTY;

    private RepairClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated repair client became visible");
            if (failure != null) {
                throw new AssertionError("Repair packet server check failed", failure);
            }
            require(++ticks < 1800, "Repair client timed out at stage " + stage);
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
                        player.giveExperienceLevels(100);
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) {
                                server.overworld().setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                            }
                        }
                        server.overworld().setBlock(ANVIL, Blocks.ANVIL.defaultBlockState(), 3);
                        player.connection.teleport(0.5, 65, 2.5, 180, 0);
                        player.getInventory().clearContent();
                        player.inventoryMenu.broadcastChanges();
                    });
                    stage = 1;
                }
                case 1 -> {
                    if (!ready || !client.level.getBlockState(ANVIL).is(Blocks.ANVIL)
                            || client.player.distanceToSqr(Vec3.atCenterOf(ANVIL)) > 16) {
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
                            new BlockHitResult(Vec3.atCenterOf(ANVIL), Direction.UP, ANVIL, false));
                    stage = 2;
                }
                case 2 -> {
                    if (!(client.player.containerMenu instanceof AnvilMenu)) {
                        return;
                    }
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        var menu = (AnvilMenu) player.containerMenu;
                        var root = RepairFixtures.mixed(false);
                        var donor = new ItemStack(Items.GOLDEN_LEGGINGS);
                        donor.setDamageValue(donor.getMaxDamage() - 10);
                        menu.getSlot(0).set(root);
                        menu.getSlot(1).set(donor);
                        menu.setItemName(root.getHoverName().getString());
                        expected = menu.getSlot(2).getItem().copy();
                        require(!expected.isEmpty(), "Packet anvil source-donor preview is empty");
                        levels = player.experienceLevel;
                        price = menu.getCost();
                        menu.broadcastChanges();
                    });
                    stage = 3;
                }
                case 3 -> {
                    if (!ready || !ItemStack.matches(expected, client.player.containerMenu.getSlot(2).getItem())) {
                        return;
                    }
                    click(client, 2);
                    stage = 4;
                }
                case 4 -> {
                    var menu = client.player.containerMenu;
                    if (!ItemStack.matches(expected, menu.getCarried()) || menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) {
                        return;
                    }
                    if (!anvilPaid) {
                        if (ready) {
                            onServer(client, () -> {
                                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                // A predicted client pickup is not proof that its packet has been processed.
                                if (player.containerMenu.getCarried().isEmpty()) {
                                    return;
                                }
                                require(ItemStack.matches(expected, player.containerMenu.getCarried())
                                                && player.experienceLevel == levels - price,
                                        "Anvil packet paid output/XP differs from preview");
                                anvilPaid = true;
                            });
                        }
                        return;
                    }
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                        player.closeContainer();
                        var root = RepairFixtures.mixed(true);
                        var donor = new ItemStack(Items.IRON_LEGGINGS);
                        donor.setDamageValue(donor.getMaxDamage() - 10);
                        player.inventoryMenu.getInputGridSlots().get(0).set(donor);
                        player.inventoryMenu.getInputGridSlots().get(3).set(root);
                        expected = player.inventoryMenu.getSlot(0).getItem().copy();
                        require(!expected.isEmpty(), "Packet inventory crafting preview is empty");
                        levels = player.experienceLevel;
                        player.inventoryMenu.broadcastChanges();
                    });
                    stage = 5;
                }
                case 5 -> {
                    if (!ready || client.player.containerMenu != client.player.inventoryMenu
                            || !ItemStack.matches(expected, client.player.inventoryMenu.getSlot(0).getItem())) {
                        return;
                    }
                    click(client, 0);
                    stage = 6;
                }
                case 6 -> {
                    if (!ItemStack.matches(expected, client.player.inventoryMenu.getCarried())) {
                        return;
                    }
                    if (!craftingPaid) {
                        if (ready) {
                            onServer(client, () -> {
                                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                                if (player.inventoryMenu.getCarried().isEmpty()) {
                                    return;
                                }
                                require(ItemStack.matches(expected, player.inventoryMenu.getCarried()) && player.experienceLevel == levels
                                                && player.inventoryMenu.getInputGridSlots().stream().noneMatch(slot -> slot.hasItem()),
                                        "Crafting packet consumed the wrong inputs, paid XP or changed output");
                                craftingPaid = true;
                            });
                        }
                        return;
                    }
                    settledAt = ticks;
                    stage = 7;
                }
                case 7 -> {
                    if (!ready || ticks - settledAt < 10) {
                        return;
                    }
                    SkeletonChecks.passed("client");
                    finished = true;
                    client.stop();
                }
                default -> throw new AssertionError("Unknown repair client stage");
            }
        } catch (Throwable error) {
            finished = true;
            SkeletonChecks.failed("client", error);
            client.stop();
        }
    }

    private static void click(Minecraft client, int slot) {
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, 0, ContainerInput.PICKUP, client.player);
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
