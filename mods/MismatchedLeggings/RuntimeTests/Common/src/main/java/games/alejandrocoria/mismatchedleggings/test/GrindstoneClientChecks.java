package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Confirm authoritative extraction and XP, not just the client's predicted pickup. */
final class GrindstoneClientChecks {
    private static int ticks;
    private static int stage;
    private static int beforePoints;
    private static int beforeOrbs;
    private static boolean finished;
    private static volatile boolean ready;
    private static volatile boolean paid;
    private static volatile Throwable failure;
    private static volatile ItemStack expected = ItemStack.EMPTY;

    private GrindstoneClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated grindstone client became visible");
            if (failure != null) {
                throw new AssertionError("Grindstone packet server check failed", failure);
            }
            require(++ticks < 1800, "Grindstone client timed out at stage " + stage);
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
                        var station = GrindstoneChecks.STATION;
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 4; z++) {
                                server.overworld().setBlock(station.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                            }
                        }
                        server.overworld().setBlock(station, Blocks.GRINDSTONE.defaultBlockState(), 3);
                        player.connection.teleport(station.getX() + 0.5, station.getY(), station.getZ() + 3.5, 180, 0);
                        player.getInventory().clearContent();
                        player.inventoryMenu.broadcastChanges();
                    });
                    stage = 1;
                }
                case 1 -> {
                    if (!ready) {
                        return;
                    }
                    var station = GrindstoneChecks.STATION;
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(station), Direction.UP, station, false));
                    stage = 2;
                }
                case 2 -> {
                    if (!(client.player.containerMenu instanceof GrindstoneMenu)) {
                        return;
                    }
                    onServer(client, () -> {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
                        var iron = new ItemStack(Items.IRON_LEGGINGS);
                        var gold = new ItemStack(Items.GOLDEN_LEGGINGS);
                        iron.enchant(protection, 1);
                        gold.enchant(protection, 1);
                        var root = MixedLeggingsFactory.createPair(iron, gold, CompositionRules.defaults()).inverse();
                        root.setDamageValue(root.getMaxDamage() - 20);
                        var menu = (GrindstoneMenu) player.containerMenu;
                        menu.getSlot(0).set(iron.copy());
                        menu.getSlot(1).set(root);
                        expected = menu.getSlot(2).getItem().copy();
                        require(!expected.isEmpty(), "Packet grindstone donor preview is empty");
                        beforePoints = player.totalExperience;
                        beforeOrbs = GrindstoneChecks.xp(server.overworld());
                        menu.broadcastChanges();
                    });
                    stage = 3;
                }
                case 3 -> {
                    if (!ready || !ItemStack.matches(expected, client.player.containerMenu.getSlot(2).getItem())) {
                        return;
                    }
                    onServer(client, () -> {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        require(player.totalExperience == beforePoints && GrindstoneChecks.xp(server.overworld()) == beforeOrbs,
                                "Grindstone preview paid XP before extraction");
                    });
                    stage = 4;
                }
                case 4 -> {
                    if (!ready) {
                        return;
                    }
                    client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 2, 0, ContainerInput.PICKUP, client.player);
                    stage = 5;
                }
                case 5 -> {
                    if (!ready || !ItemStack.matches(expected, client.player.containerMenu.getCarried())) {
                        return;
                    }
                    if (!paid) {
                        onServer(client, () -> {
                            var server = client.getSingleplayerServer();
                            var player = server.getPlayerList().getPlayers().getFirst();
                            var menu = player.containerMenu;
                            if (menu.getCarried().isEmpty()) {
                                return;
                            }
                            int gained = player.totalExperience - beforePoints + GrindstoneChecks.xp(server.overworld()) - beforeOrbs;
                            require(ItemStack.matches(expected, menu.getCarried()) && !menu.getSlot(0).hasItem()
                                            && !menu.getSlot(1).hasItem() && gained == 1,
                                    "Grindstone packet changed paid output, consumption or bounded XP");
                            paid = true;
                        });
                        return;
                    }
                    SkeletonChecks.passed("client");
                    finished = true;
                    client.stop();
                }
                default -> throw new AssertionError("Unknown grindstone client stage");
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
