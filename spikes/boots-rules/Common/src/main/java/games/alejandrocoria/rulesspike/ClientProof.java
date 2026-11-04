package games.alejandrocoria.rulesspike;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

/** Observes real native joins/packets. Viewer ordering below is a state-contract probe, not JEI/REI acceptance. */
public final class ClientProof {
    private static int ticks;
    private static int session;
    private static int phase;
    private static boolean finished;
    private static boolean joined;
    private static boolean disconnectObserved;

    private ClientProof() {
    }

    public static WorkbenchInput input() {
        return new WorkbenchInput(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
    }

    public static void joining() {
        disconnectObserved = false;
        if (Proof.CANDIDATE) {
            PreviewState.reset();
            Proof.require(!new MixedBootsWorkbenchRecipe().assemblePreview(input(), null).isComplete(),
                    "Direct preview produced output before rules arrived");
        }
        joined = true;
    }

    public static void disconnected() {
        if (Proof.CANDIDATE) {
            PreviewState.reset();
        }
        disconnectObserved = true;
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            Proof.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client became visible");
            Proof.require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F, "Automated client is not muted");
            Proof.require(++ticks < 3600, "Client timed out at phase " + phase + ": " + client.screen);
            if (Proof.CASE.equals("new-old") || Proof.CASE.equals("old-new")) {
                if (client.screen instanceof DisconnectedScreen screen) {
                    String reason = screen.getNarrationMessage().getString();
                    Proof.require(reason.contains("Mismatched Boots") || reason.contains("mismatchedboots"),
                            "Rejection did not identify Boots: " + reason);
                    Proof.require(PreviewState.current() == null, "Rejected peer retained rules");
                    Proof.write("client-ok.txt", "rejected: " + reason + "; play join observed=" + joined);
                    finished = true;
                    client.stop();
                }
                return;
            }
            if (phase == 1) {
                // Clearing the level can precede the native TCP disconnect event by a tick.
                if (!disconnectObserved || client.player != null || client.level != null || client.getSingleplayerServer() != null) {
                    return;
                }
                Proof.require(PreviewState.current() == null, "Disconnect retained rules");
                Proof.require(!new MixedBootsWorkbenchRecipe().assemblePreview(input(), null).isComplete(),
                        "Disconnected direct preview produced output");
                if (Proof.CASE.equals("integrated") && session == 1) {
                    Files.writeString(client.gameDirectory.toPath().resolve("config/mismatchedboots-attributes.toml"),
                            "[attributes]\nfallback = 'minimum'\n[attributes.\"minecraft:armor\"]\npolicy = 'minimum'\n");
                    phase = 0;
                    client.createWorldOpenFlows().openWorld("spike", () -> client.setScreen(new TitleScreen()));
                    return;
                }
                Proof.require(PreviewState.received == session, "Expected one complete rules packet per connection");
                Proof.write("client-ok.txt", "complete snapshot, server authority, direct preview gate, codec bounds, disconnect; sessions=" + session);
                finished = true;
                client.stop();
                return;
            }
            if (client.player == null || client.level == null || PreviewState.current() == null) {
                return;
            }
            var snapshot = PreviewState.current();
            AttributePolicy expectedPolicy = session == 1 ? AttributePolicy.MINIMUM : AttributePolicy.MAXIMUM;
            Proof.require(snapshot.attributes().fallback() == expectedPolicy, "Client did not receive server policy");
            Proof.require(client.hasSingleplayerServer() == Proof.CASE.equals("integrated"), "Wrong connection kind");
            var preview = new MixedBootsWorkbenchRecipe().assemblePreview(input(), client.level);
            Proof.require(preview.isComplete(), "Complete snapshot did not enable direct preview");
            ItemStack received = client.player.getInventory().getItem(0);
            if (received.isEmpty() || !MismatchedWorkbenchClientRecipes.hasRecipes()) {
                return;
            }
            Proof.require(ItemStack.isSameItemSameComponents(preview.primary(), received),
                    "Client preview differs from actual server-created stack");
            Proof.require(MismatchedWorkbenchClientRecipes.assemblePair(input(), client.level).isComplete(),
                    "Actual recipe cache did not refresh after synchronization");
            codecChecks(client, snapshot);
            orderingChecks(snapshot);
            // Even on the physical client, resetting preview must not reset the integrated server's rules.
            if (client.getSingleplayerServer() != null) {
                Proof.require(Snapshot.server().attributes().fallback() == expectedPolicy, "Client state modified server policy");
            }
            session++;
            phase = 1;
            // The saving-screen helper alone tears down the level, but does not request a TCP disconnect.
            client.disconnectFromWorld(net.minecraft.network.chat.Component.literal("Rules spike session complete"));
        } catch (Throwable failure) {
            Proof.write("client-failure.txt", failure.toString());
            failure.printStackTrace();
            finished = true;
            client.stop();
        }
    }

    private static void orderingChecks(Snapshot value) {
        int[] refresh = {0};
        PreviewState.listen(() -> refresh[0]++);
        Proof.require(refresh[0] == 1, "Late rules listener not refreshed");
        PreviewState.reset();
        Proof.require(refresh[0] == 2, "Reset did not invalidate examples");
        // Model recipe-before-rules and rules-before-recipes with one immutable publication.
        PreviewState.install(value);
        Proof.require(refresh[0] == 3, "Existing examples not refreshed on publication");
        int[] late = {0};
        PreviewState.listen(() -> late[0]++);
        Proof.require(late[0] == 1, "Rules-before-recipes did not refresh immediately");
        PreviewState.received--; // Local contract probe is not a received network packet.
    }

    private static void codecChecks(Minecraft client, Snapshot snapshot) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), client.level.registryAccess());
        try {
            Snapshot.CODEC.encode(buffer, snapshot);
            int length = buffer.readableBytes();
            var decoded = Snapshot.CODEC.decode(buffer);
            Proof.require(snapshot.attributes().equals(decoded.attributes())
                    && snapshot.components().equals(decoded.components()), "Snapshot codec changed rules");
            buffer.readerIndex(0);
            buffer.writeByte(0);
            reject(buffer, "trailing");
            buffer.clear();
            buffer.writeVarInt(Integer.MAX_VALUE);
            reject(buffer, "section length");
            buffer.clear();
            buffer.writeZero(524_289);
            reject(buffer, "total budget");
            Proof.require(length > 0, "Snapshot was empty");
        } finally {
            buffer.release();
        }
    }

    private static void reject(RegistryFriendlyByteBuf buffer, String name) {
        try {
            Snapshot.CODEC.decode(buffer);
            throw new AssertionError("Accepted invalid " + name);
        } catch (DecoderException expected) {
            // Explicit bounded wire input, not a check of a Java library invariant.
        }
    }
}
