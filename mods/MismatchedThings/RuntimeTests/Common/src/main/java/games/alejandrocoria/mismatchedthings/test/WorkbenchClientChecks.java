package games.alejandrocoria.mismatchedthings.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Native block-use and container packets against a world saved by a separate server JVM. */
public final class WorkbenchClientChecks {
    private static int ticks;
    private static int stage;
    private static int openedAt;
    private static int routeIndex;
    private static volatile boolean ready;
    private static volatile String expectedInventory;
    private static volatile String expectedPrimary;
    private static volatile String expectedInverse;
    private static volatile Throwable serverFailure;
    private static boolean stopping;

    private WorkbenchClientChecks() {
    }

    public static void tick(Minecraft client) {
        if (stopping) {
            return;
        }
        try {
            WorkbenchChecks.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            WorkbenchChecks.require(serverFailure == null, "Integrated server assertion failed: " + serverFailure);
            WorkbenchChecks.require(++ticks <= 2400, "Client timed out at stage " + stage + ", screen=" + client.screen);
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
                        var level = server.overworld();
                        WorkbenchChecks.require(level.getBlockState(WorkbenchChecks.TABLE).is(MismatchedWorkbench.block()),
                                "Placed station did not survive separate JVMs");
                        var barrel = (Container) level.getBlockEntity(WorkbenchChecks.BARREL);
                        WorkbenchChecks.require(barrel.getItem(0).is(MismatchedWorkbench.item()), "Saved station item missing");
                        WorkbenchCoexistence.verifyEnvironment();
                        WorkbenchCoexistence.verifySavedRoot(level, barrel);
                        player.getInventory().clearContent();
                        player.connection.teleport(0.5, 65.0, 2.5, 180.0F, 0.0F);
                        ready = true;
                    });
                    stage = 1;
                }
                case 1 -> {
                    if (!ready || client.player.distanceToSqr(0.5, 65.0, 2.5) > 4.0
                            || !client.level.getBlockState(WorkbenchChecks.TABLE).is(MismatchedWorkbench.block())) {
                        return;
                    }
                    ready = false;
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(WorkbenchChecks.TABLE), Direction.UP, WorkbenchChecks.TABLE, false));
                    openedAt = ticks;
                    stage = 2;
                }
                case 2 -> {
                    if (!(client.player.containerMenu instanceof WorkbenchMenu menu) || !(client.screen instanceof WorkbenchScreen)) {
                        WorkbenchChecks.require(ticks - openedAt <= 120, "Native block-use did not open the shared screen");
                        return;
                    }
                    WorkbenchChecks.require(client.screen.getTitle().getString().equals("Mismatched Workbench"), "Unlocalized title");
                    if (WorkbenchCoexistence.routes().isEmpty()) {
                        WorkbenchChecks.require(!menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_HELMET))
                                && !menu.getSlot(2).hasItem() && !menu.getSlot(3).hasItem(), "Core invented a consumer recipe");
                        finish(client, "standalone screen, empty admission, separate-JVM station/item persistence passed\n");
                        return;
                    }
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        var route = WorkbenchCoexistence.routes().get(routeIndex);
                        var expected = route.assemble(player.level());
                        expectedPrimary = WorkbenchChecks.encode(expected.primary(), player.level());
                        expectedInverse = WorkbenchChecks.encode(expected.inverse(), player.level());
                        player.getInventory().clearContent();
                        player.getInventory().setItem(0, route.input().left());
                        player.getInventory().setItem(1, route.input().right());
                        player.containerMenu.broadcastChanges();
                        ready = true;
                    });
                    stage = 3;
                }
                case 3 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    var route = WorkbenchCoexistence.routes().get(routeIndex);
                    if (!ready || !menu.getSlot(31).getItem().is(route.first()) || !menu.getSlot(32).getItem().is(route.second())) {
                        return;
                    }
                    click(client, menu, 31);
                    click(client, menu, 32);
                    ready = false;
                    stage = 4;
                }
                case 4 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (!menu.getSlot(2).hasItem() || !menu.getSlot(3).hasItem()) {
                        return;
                    }
                    WorkbenchChecks.require(sameStack(menu.getSlot(2).getItem(), expectedPrimary, client.level)
                            && sameStack(menu.getSlot(3).getItem(), expectedInverse, client.level), "Wrong complete ordered previews");
                    click(client, menu, 2);
                    stage = 5;
                }
                case 5 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (!menu.isCommitted() || menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) {
                        return;
                    }
                    WorkbenchChecks.require(!menu.getSlot(0).isActive() && menu.getSlot(3).hasItem(), "Commit lost inverse or unlocked inputs");
                    click(client, menu, 3);
                    stage = 6;
                }
                case 6 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (menu.getSlot(2).hasItem() || menu.getSlot(3).hasItem()) {
                        return;
                    }
                    if (ready) {
                        stage = 7;
                        return;
                    }
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        // Empty client slots may be prediction: wait for both actual server transfers before comparing inventory.
                        if (player.containerMenu.getSlot(2).hasItem() || player.containerMenu.getSlot(3).hasItem()
                                || player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).count() != 2) {
                            return;
                        }
                        var stacks = player.getInventory().getNonEquipmentItems();
                        WorkbenchChecks.require(stacks.stream().anyMatch(stack -> sameStack(stack, expectedPrimary, player.level()))
                                && stacks.stream().anyMatch(stack -> sameStack(stack, expectedInverse, player.level())),
                                "Paid outputs differ from the authoritative complete recipe results");
                        expectedInventory = inventory(player, player.level());
                        ready = true;
                    });
                }
                case 7 -> {
                    if (!JsonParser.parseString(expectedInventory).equals(JsonParser.parseString(inventory(client.player, client.level)))) {
                        return;
                    }
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    WorkbenchChecks.require(!menu.isCommitted() && menu.getSlot(0).isActive(), "Completed transaction did not unlock inputs");
                    ready = false;
                    onServer(client, () -> {
                        WorkbenchChecks.verifyCloseCleanup(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),
                                WorkbenchCoexistence.routes().get(routeIndex));
                        ready = true;
                    });
                    stage = 8;
                }
                case 8 -> {
                    if (ready) {
                        ready = false;
                        if (++routeIndex < WorkbenchCoexistence.routes().size()) {
                            stage = 2;
                        } else {
                            finish(client, "native screen/menu packets, ordered paid outputs, exactly-once payment, complete server/client stacks and close cleanup passed\n");
                        }
                    }
                }
                default -> throw new AssertionError("Unexpected stage " + stage);
            }
        } catch (Throwable failure) {
            stopping = true;
            WorkbenchChecks.fail(failure);
            client.stop();
        }
    }

    private static void click(Minecraft client, WorkbenchMenu menu, int slot) {
        client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, client.player);
    }

    private static boolean sameStack(ItemStack stack, String expected, Level level) {
        return !stack.isEmpty() && JsonParser.parseString(WorkbenchChecks.encode(stack, level)).equals(JsonParser.parseString(expected));
    }

    private interface ServerCheck {
        void run() throws Exception;
    }

    private static void onServer(Minecraft client, ServerCheck action) {
        client.getSingleplayerServer().execute(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                serverFailure = failure;
                WorkbenchChecks.fail(failure);
            }
        });
    }

    private static String inventory(Player player, Level level) {
        JsonArray snapshot = new JsonArray();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            snapshot.add(stack.isEmpty() ? JsonNull.INSTANCE : JsonParser.parseString(WorkbenchChecks.encode(stack, level)));
        }
        return snapshot.toString();
    }

    private static void finish(Minecraft client, String message) {
        WorkbenchChecks.write("client.ok", message);
        stopping = true;
        client.stop();
    }
}
