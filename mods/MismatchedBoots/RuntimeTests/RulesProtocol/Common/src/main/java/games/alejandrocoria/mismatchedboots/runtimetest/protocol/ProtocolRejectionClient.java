package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

/** Observes native screens only: no channel registrations, packet handlers or replacement error messages. */
public final class ProtocolRejectionClient {
    private static int ticks;
    private static boolean joined;
    private static boolean finished;

    private ProtocolRejectionClient() {
    }

    public static void joined() {
        joined = true;
    }

    public static void tick(Minecraft client, boolean neoForge, String nativeChannels) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3_600, "Protocol rejection timed out");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Protocol observer became visible");
            require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0F, "Client is not muted");
            require(!joined && client.level == null && client.player == null, "Incompatible client entered gameplay");

            String reason;
            if (nativeChannels != null) {
                if (Boolean.getBoolean("mismatchedboots.runtimetest.protocolNativeControl")) {
                    require(nativeChannels.contains("mismatchedboots_protocol_test:native_control"),
                            "Native mismatch screen omitted the synthetic control route: " + nativeChannels);
                } else {
                    require(nativeChannels.contains("mismatchedboots:ready_v2")
                            && nativeChannels.contains("mismatchedboots:rules_v2"),
                            "Native mismatch screen omitted the required Boots routes: " + nativeChannels);
                }
                reason = nativeChannels;
            } else if (client.screen instanceof DisconnectedScreen screen) {
                require(!neoForge, "NeoForge did not use its native mod/channel mismatch screen");
                reason = screen.getNarrationMessage().getString();
                require(reason.contains("Mismatched Boots") && reason.contains("compatible network protocols"),
                        "Not the specific Fabric Boots protocol rejection: " + reason);
            } else {
                return;
            }
            ProtocolEvidence.write("client-protocol-rejected.txt", reason);
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            ProtocolEvidence.write("client-failure.txt", failure.toString());
            failure.printStackTrace();
            finished = true;
            client.stop();
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
