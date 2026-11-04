package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

/** Passive packaged-product acceptance: no replacement payloads, receivers or preview hooks. */
final class IntegratedRulesClientTest {
    private static MinecraftServer lastServer;
    private static boolean delivered;
    private static int ticks;
    private static int session;
    private static boolean waitingForDisconnect;
    private static boolean finished;

    private IntegratedRulesClientTest() {
    }

    static void serverTick(MinecraftServer server) {
        if (server != lastServer) {
            lastServer = server;
            delivered = false;
        }
        if (!delivered && !server.getPlayerList().getPlayers().isEmpty()) {
            var pair = new MixedBootsWorkbenchRecipe().assemblePair(input());
            require(pair.isComplete(), "Integrated server did not compose the pair");
            server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0, pair.primary());
            delivered = true;
        }
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3_600, "Integrated rules test timed out in session " + session);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client became visible");
            require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F, "Client is not muted");

            if (waitingForDisconnect) {
                if (client.level != null || client.player != null || client.getSingleplayerServer() != null) {
                    return;
                }
                require(CompositionRulesClientLifecycle.synchronizedRules() == null, "Disconnect retained old rules");
                require(!new MixedBootsWorkbenchRecipe().assemblePreview(input(), null).isComplete(),
                        "Disconnected direct preview produced output");
                require(!MismatchedWorkbenchClientRecipes.hasRecipes(), "Disconnect retained old recipes");
                if (session == 2) {
                    NetworkAcceptanceFiles.write("integrated-rules-success.txt",
                            "two native sessions, changed server rules, complete preview, server-created stack, disconnect reset\n");
                    finished = true;
                    client.stop();
                    return;
                }
                Files.writeString(client.gameDirectory.toPath().resolve("config/mismatchedboots-attributes.toml"),
                        "[attributes]\nfallback = 'minimum'\n[attributes.\"minecraft:armor\"]\npolicy = 'minimum'\n");
                waitingForDisconnect = false;
                client.createWorldOpenFlows().openWorld("rules", () -> client.setScreen(new TitleScreen()));
                return;
            }

            var snapshot = CompositionRulesClientLifecycle.synchronizedRules();
            if (client.player == null || client.level == null || snapshot == null
                    || !MismatchedWorkbenchClientRecipes.hasRecipes()) {
                return;
            }
            AttributePolicy expected = session == 0 ? AttributePolicy.MAXIMUM : AttributePolicy.MINIMUM;
            require(client.hasSingleplayerServer(), "Fixture did not open an integrated server");
            require(snapshot.attributes().fallback() == expected, "Rejoin reused old attribute rules");
            require(snapshot.components().durability() == DurabilityPolicy.SEPARATE
                    && snapshot.components().repairCost() == RepairCostPolicy.OWNER, "Incomplete component rules");
            ItemStack received = client.player.getInventory().getItem(0);
            var direct = new MixedBootsWorkbenchRecipe().assemblePreview(input(), client.level);
            require(direct.isComplete(), "Complete rules did not enable the direct preview");
            // A rejoin can first synchronize the persisted inventory from the previous session.
            if (!ItemStack.isSameItemSameComponents(direct.primary(), received)) {
                return;
            }
            var cached = MismatchedWorkbenchClientRecipes.assemblePair(input(), client.level);
            require(cached.isComplete() && ItemStack.isSameItemSameComponents(cached.primary(), received),
                    "Loaded recipe preview differs from the actual server-created stack");
            // The server owns its policy even on the physical client; inspect on its own thread.
            client.getSingleplayerServer().submit(() ->
                    require(AttributeRulesLifecycle.serverRules().fallback() == expected,
                            "Client snapshot changed server authority")).join();
            session++;
            waitingForDisconnect = true;
            client.disconnectFromWorld(Component.literal("Integrated rules session complete"));
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("integrated-rules", failure);
            failure.printStackTrace();
            finished = true;
            client.stop();
        }
    }

    private static WorkbenchInput input() {
        return new WorkbenchInput(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
