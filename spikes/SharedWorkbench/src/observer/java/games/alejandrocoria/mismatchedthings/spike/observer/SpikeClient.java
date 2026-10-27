package games.alejandrocoria.mismatchedthings.spike.observer;

import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchCore;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchMenu;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchScreen;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

/** Actual block-use/menu/slot packets; no custom menu transport or second client recipe engine. */
public final class SpikeClient {
    private static int ticks;
    private static int stage;
    private static int route;
    private static int openedAt;
    private static volatile boolean ready;
    private static volatile String expected;
    private static boolean stopping;
    private static final List<Boolean> ROUTES = new ArrayList<>();

    private SpikeClient() {
    }

    public static void tick(Minecraft client) {
        if (stopping) {
            return;
        }
        try {
            GLFW.glfwHideWindow(client.getWindow().handle());
            if (++ticks > 2400) {
                throw new AssertionError("Client timed out at stage " + stage + ", screen=" + client.screen);
            }
            if (client.player == null || client.level == null || client.gameMode == null || client.getSingleplayerServer() == null) {
                return;
            }
            if (stage == 0) {
                if (client.screen != null) { return; }
                if (SpikeChecks.boots) { ROUTES.add(true); }
                if (SpikeChecks.addon) { ROUTES.add(false); }
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var level = server.overworld();
                        boolean upgrade = System.getProperty("workbench.spike.phase").equals("upgrade-client");
                        SpikeChecks.require(SpikeChecks.version.equals(SpikeChecks.addon ? "1.1.0" : "1.0.0"), "Wrong client core version");
                        SpikeChecks.require(level.getBlockState(SpikeChecks.TABLE).is(WorkbenchCore.mismatchedWorkbenchBlock().orElseThrow()),
                                "Placed station did not survive separate server/client JVMs");
                        var barrel = (Container) level.getBlockEntity(SpikeChecks.BARREL);
                        SpikeChecks.require(barrel.getItem(0).is(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getValue(WorkbenchCore.MISMATCHED_WORKBENCH_ID)), "Saved station item missing");
                        if (SpikeChecks.boots) {
                            SpikeChecks.require(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(barrel.getItem(1).getItem())
                                    .equals(Identifier.parse("mismatchedboots:mixed_boots")), "Saved real Boots root missing");
                            if (upgrade) {
                                String saved = Files.readString(Path.of(System.getProperty("workbench.spike.seedReport"), "saved-boots.json"));
                                SpikeChecks.require(JsonParser.parseString(saved).equals(JsonParser.parseString(SpikeChecks.encode(barrel.getItem(1), level))),
                                        "Old-core saved Boots root changed during core upgrade");
                            }
                        }
                        if (SpikeChecks.addon && !upgrade) {
                            SpikeChecks.require(barrel.getItem(2).getHoverName().getString().equals("Sibling primary"), "Saved sibling result missing");
                        }
                        player.getInventory().clearContent();
                        player.connection.teleport(0.5, 65.0, 2.5, 180.0F, 0.0F);
                        ready = true;
                    } catch (Throwable failure) { SpikeChecks.fail(failure); }
                });
                stage = 1;
            } else if (stage == 1 && ready) {
                if (client.player.distanceToSqr(0.5, 65.0, 2.5) > 4.0
                        || !client.level.getBlockState(SpikeChecks.TABLE).is(WorkbenchCore.mismatchedWorkbenchBlock().orElseThrow())) { return; }
                ready = false;
                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(SpikeChecks.TABLE), Direction.UP, SpikeChecks.TABLE, false));
                openedAt = ticks;
                stage = 2;
            } else if (stage == 2 && client.player.containerMenu instanceof WorkbenchMenu && client.screen instanceof WorkbenchScreen) {
                SpikeChecks.require(client.screen.getTitle().getString().equals("Mismatched Workbench"), "Family title is not localized");
                seed(client);
                stage = 3;
            } else if (stage == 2 && ticks - openedAt > 120) {
                throw new AssertionError("Block-use did not open menu; player=" + client.player.position()
                        + ", block=" + client.level.getBlockState(SpikeChecks.TABLE));
            } else if (stage == 3 && ready) {
                var menu = (WorkbenchMenu) client.player.containerMenu;
                if (menu.getSlot(31).getItem().isEmpty() || menu.getSlot(32).getItem().isEmpty()) { return; }
                client.gameMode.handleContainerInput(menu.containerId, 31, 0, ContainerInput.QUICK_MOVE, client.player);
                client.gameMode.handleContainerInput(menu.containerId, 32, 0, ContainerInput.QUICK_MOVE, client.player);
                ready = false;
                stage = 4;
            } else if (stage == 4) {
                var menu = (WorkbenchMenu) client.player.containerMenu;
                if (menu.getSlot(2).getItem().isEmpty() || menu.getSlot(3).getItem().isEmpty()) { return; }
                checkOutput(menu.getSlot(2).getItem(), ROUTES.get(route));
                client.gameMode.handleContainerInput(menu.containerId, 2, 0, ContainerInput.QUICK_MOVE, client.player);
                stage = 5;
            } else if (stage == 5) {
                var menu = (WorkbenchMenu) client.player.containerMenu;
                if (!menu.isCommitted() || !menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()) { return; }
                SpikeChecks.require(!menu.getSlot(0).isActive() && !menu.getSlot(3).getItem().isEmpty(), "Commit did not lock inputs/preserve inverse");
                client.gameMode.handleContainerInput(menu.containerId, 3, 0, ContainerInput.QUICK_MOVE, client.player);
                stage = 6;
            } else if (stage == 6) {
                var menu = (WorkbenchMenu) client.player.containerMenu;
                if (!menu.getSlot(2).getItem().isEmpty() || !menu.getSlot(3).getItem().isEmpty()) { return; }
                if (ready) {
                    stage = 7;
                    return;
                }
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        // The client's empty output can be prediction, ahead of the second server click.
                        // Capture only the committed server inventory, never a partial predicted transaction.
                        if (!player.containerMenu.getSlot(2).getItem().isEmpty()
                                || !player.containerMenu.getSlot(3).getItem().isEmpty()
                                || player.getInventory().getNonEquipmentItems().stream().filter(value -> !value.isEmpty()).count() != 2) {
                            return;
                        }
                        expected = inventory(player, player.level());
                        ready = true;
                    } catch (Throwable failure) { SpikeChecks.fail(failure); }
                });
            } else if (stage == 7 && ready) {
                if (!JsonParser.parseString(expected).equals(JsonParser.parseString(inventory(client.player, client.level)))) { return; }
                long count = client.player.getInventory().getNonEquipmentItems().stream().filter(value -> !value.isEmpty()).count();
                SpikeChecks.require(count == 2, "Transaction did not produce exactly two paid outputs");
                ready = false;
                if (++route < ROUTES.size()) {
                    seed(client);
                    stage = 3;
                } else {
                    SpikeChecks.write(System.getProperty("workbench.spike.phase") + ".ok", "core=" + SpikeChecks.version + "\nroutes=" + ROUTES.size()
                            + "\nseparate-JVM station/item/root persistence, native menu packets, paid ordered outputs and full-stack packets verified\n");
                    stopping = true;
                    client.stop();
                }
            }
        } catch (Throwable failure) {
            stopping = true;
            SpikeChecks.fail(failure);
            client.stop();
        }
    }

    private static String inventory(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level) {
        var snapshot = new com.google.gson.JsonArray();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            snapshot.add(stack.isEmpty() ? com.google.gson.JsonNull.INSTANCE : JsonParser.parseString(SpikeChecks.encode(stack, level)));
        }
        return snapshot.toString();
    }

    private static void seed(Minecraft client) {
        boolean boots = ROUTES.get(route);
        client.getSingleplayerServer().execute(() -> {
            try {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(boots ? Items.IRON_BOOTS : Items.IRON_HELMET));
                player.getInventory().setItem(1, new ItemStack(boots ? Items.GOLDEN_BOOTS : Items.GOLDEN_HELMET));
                player.containerMenu.broadcastChanges();
                ready = true;
            } catch (Throwable failure) { SpikeChecks.fail(failure); }
        });
    }

    private static void checkOutput(ItemStack stack, boolean boots) {
        if (boots) {
            SpikeChecks.require(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .equals(Identifier.parse("mismatchedboots:mixed_boots")), "Real Boots factory was not used");
        } else {
            SpikeChecks.require(stack.getHoverName().getString().equals("Sibling primary"), "Sibling provider was not used");
        }
    }
}
