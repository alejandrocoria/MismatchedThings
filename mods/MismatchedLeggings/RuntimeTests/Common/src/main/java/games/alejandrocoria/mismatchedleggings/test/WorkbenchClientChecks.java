package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real block-use/container packets and complete synchronized previews, not direct client rule installs. */
public final class WorkbenchClientChecks {
    private static int ticks;
    private static int stage;
    private static int closedAt;
    private static boolean boots;
    private static boolean secondSession;
    private static boolean finished;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static volatile WorkbenchResult expected;
    private static WorkbenchResult paid;

    private WorkbenchClientChecks() {
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated workbench client became visible");
            if (serverFailure != null) {
                throw new AssertionError("Server check failed", serverFailure);
            }
            require(++ticks < 2400, "Workbench client timed out at stage " + stage + ": " + client.screen);
            if (stage == 10) {
                if (client.player != null || client.level != null || client.getSingleplayerServer() != null) {
                    return;
                }
                require(CompositionRuleState.synchronizedPreview() == null, "Disconnect retained server preview state");
                require(CompositionRuleState.server().equals(CompositionRules.defaults()),
                        "Server stop retained the previous session's rules");
                require(!new MixedLeggingsWorkbenchRecipe().assemblePreview(WorkbenchChecks.input(false), null).isComplete(),
                        "Unsynchronized preview fabricated output from local defaults");
                if (!secondSession) {
                    secondSession = true;
                    boots = false;
                    ready = false;
                    stage = 0;
                    // Same native world-open flow used by Boots' lifecycle fixtures.
                    client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                    return;
                }
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
                return;
            }
            if (client.player == null || client.level == null || client.gameMode == null || client.getSingleplayerServer() == null) {
                return;
            }

            switch (stage) {
                case 0 -> {
                    if (client.screen != null || !synchronizedPolicy(sessionPolicy())) {
                        return;
                    }
                    require(new MixedLeggingsWorkbenchRecipe().assemblePreview(WorkbenchChecks.input(false), client.level).isComplete(),
                            "Synchronized client recipe preview was empty");
                    ready = false;
                    onServer(client, () -> {
                        var server = client.getSingleplayerServer();
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.getInventory().clearContent();
                        player.connection.teleport(0.5, 65, 2.5, 180, 0);
                        var input = WorkbenchChecks.input(boots);
                        player.getInventory().setItem(0, input.left());
                        player.getInventory().setItem(1, input.right());
                        player.inventoryMenu.broadcastChanges();
                        expected = WorkbenchChecks.assemble(server, boots);
                        ready = true;
                    });
                    stage = 1;
                }
                case 1 -> {
                    if (!ready || client.player.getInventory().getItem(0).isEmpty()) {
                        return;
                    }
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(WorkbenchChecks.TABLE), Direction.UP, WorkbenchChecks.TABLE, false));
                    stage = 2;
                }
                case 2 -> {
                    if (!(client.player.containerMenu instanceof WorkbenchMenu menu)
                            || !menu.getSlot(31).hasItem() || !menu.getSlot(32).hasItem()) {
                        return;
                    }
                    click(client, 31);
                    click(client, 32);
                    stage = 3;
                }
                case 3 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (!matches(menu, expected)) {
                        return;
                    }
                    ready = false;
                    onServer(client, () -> {
                        if (!secondSession && !boots) {
                            WorkbenchChecks.writeRules(Path.of("config"), "minimum");
                        }
                        require(CompositionRuleState.server().attributes().fallback()
                                        == AttributePolicy.fromSerializedName(sessionPolicy()).orElseThrow(),
                                "Editing config files changed the running server's rules");
                        ready = true;
                    });
                    stage = 4;
                }
                case 4 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (!ready || !synchronizedPolicy(sessionPolicy()) || !matches(menu, expected)) {
                        return;
                    }
                    if (!boots) {
                        var preview = new MixedLeggingsWorkbenchRecipe().assemblePreview(WorkbenchChecks.input(false), client.level);
                        require(ItemStack.matches(preview.primary(), expected.primary())
                                && ItemStack.matches(preview.inverse(), expected.inverse()), "Client config overrode server-authoritative preview");
                    }
                    paid = expected;
                    click(client, boots ? 3 : 2);
                    stage = 5;
                }
                case 5 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (!menu.isCommitted() || menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) {
                        return;
                    }
                    require(!menu.getSlot(0).isActive(), "Paid inverse failed to lock inputs on the client");
                    ready = false;
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        require(WorkbenchChecks.inventory(player).size() == 1, "First packet did not pay exactly once");
                        ready = true;
                    });
                    stage = 6;
                }
                case 6 -> {
                    if (!ready || !synchronizedPolicy(sessionPolicy())) {
                        return;
                    }
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    require(ItemStack.matches(menu.getSlot(boots ? 2 : 3).getItem(), boots ? paid.primary() : paid.inverse()),
                            "Editing configuration changed an already paid result");
                    click(client, boots ? 2 : 3);
                    stage = 7;
                }
                case 7 -> {
                    var menu = (WorkbenchMenu) client.player.containerMenu;
                    if (menu.isCommitted() || menu.getSlot(2).hasItem() || menu.getSlot(3).hasItem()) {
                        return;
                    }
                    long count = client.player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).count();
                    if (count != 2) {
                        return;
                    }
                    require(client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(stack, paid.primary()))
                            && client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(stack, paid.inverse())),
                            "Paid client outputs differ from the complete authoritative previews");
                    ready = false;
                    onServer(client, () -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        require(WorkbenchChecks.inventory(player).size() == 2 && WorkbenchChecks.contains(player, paid.primary())
                                && WorkbenchChecks.contains(player, paid.inverse()), "Server paid outputs were duplicated or changed");
                        ready = true;
                    });
                    stage = 8;
                }
                case 8 -> {
                    if (!ready) {
                        return;
                    }
                    client.player.closeContainer();
                    if (!boots && Boolean.getBoolean("leggings.test.boots")) {
                        boots = true;
                        stage = 0;
                    } else {
                        // Let the native close packet reach the server before tearing down Netty.
                        closedAt = ticks;
                        stage = 9;
                    }
                }
                case 9 -> {
                    if (ticks - closedAt < 10) {
                        return;
                    }
                    client.disconnectWithSavingScreen();
                    stage = 10;
                }
                default -> throw new AssertionError("Unknown stage " + stage);
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean synchronizedPolicy(String name) {
        var rules = CompositionRuleState.synchronizedPreview();
        return rules != null && rules.attributes().fallback() == AttributePolicy.fromSerializedName(name).orElseThrow()
                && rules.components().durability() == (name.equals("maximum") ? DurabilityPolicy.SEPARATE : DurabilityPolicy.AVERAGE);
    }

    private static String sessionPolicy() {
        return secondSession ? "minimum" : "maximum";
    }

    private static boolean matches(WorkbenchMenu menu, WorkbenchResult expected) {
        return ItemStack.matches(menu.getSlot(2).getItem(), expected.primary())
                && ItemStack.matches(menu.getSlot(3).getItem(), expected.inverse());
    }

    private static void click(Minecraft client, int slot) {
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, client.player);
    }

    private interface ServerAction {
        void run() throws Exception;
    }

    private static void onServer(Minecraft client, ServerAction action) {
        client.getSingleplayerServer().execute(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }
}
