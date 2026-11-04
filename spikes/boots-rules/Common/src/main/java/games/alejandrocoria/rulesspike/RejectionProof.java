package games.alejandrocoria.rulesspike;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

/** Passive historical-client observer: no references to current Boots/Things API or preview implementations. */
public final class RejectionProof {
    private static int ticks;
    private static boolean finished;

    private RejectionProof() {
    }

    public static void acceptNative(Minecraft client, String reason) {
        if (finished) {
            return;
        }
        try {
            Proof.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Rejected client became visible");
            Proof.require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F, "Rejected client is not muted");
            Proof.require(client.level == null && client.player == null, "Rejected client entered gameplay");
            Proof.require(reason.contains("mismatchedboots:ready_v2") && reason.contains("mismatchedboots:rules_v2"),
                    "Native rejection did not identify both required Boots routes: " + reason);
            Proof.write("client-ok.txt", "native rejection before gameplay: " + reason);
        } catch (Throwable failure) {
            Proof.write("client-failure.txt", failure.toString());
            failure.printStackTrace();
        }
        finished = true;
        client.stop();
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            Proof.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Historical client became visible");
            Proof.require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F, "Historical client is not muted");
            Proof.require(++ticks < 3600, "Historical client was not rejected");
            Proof.require(client.level == null && client.player == null, "Historical client entered gameplay");
            if (client.screen instanceof DisconnectedScreen screen) {
                String reason = screen.getNarrationMessage().getString();
                Proof.require(reason.contains("Mismatched Boots") || reason.contains("mismatchedboots"),
                        "Rejection did not identify Boots: " + reason);
                Proof.write("client-ok.txt", "rejected before gameplay: " + reason);
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            Proof.write("client-failure.txt", failure.toString());
            failure.printStackTrace();
            finished = true;
            client.stop();
        }
    }
}
