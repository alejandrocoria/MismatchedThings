package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Client-only half of the Boots-alone packaged viewer acceptance. */
final class BootsOnlyViewerClientChecks {
    private static boolean clientFinished;
    private static int ticks;
    private static int state;
    private static ViewerProbe probe;

    private BootsOnlyViewerClientChecks() {
    }

    public static void clientTick(Minecraft client) {
        if (clientFinished) {
            return;
        }
        try {
            require(++ticks < 5000, "Boots-only viewer timed out at state " + state);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated Boots-only client became visible");
            if (state == 1) {
                if (client.level != null || client.getSingleplayerServer() != null) {
                    return;
                }
                if (probe.ready()) {
                    require(probe.cycles("mismatchedthings").isEmpty(), "Disconnected Boots contribution remained visible");
                }
                state = 2;
                client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                return;
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) {
                return;
            }
            if (System.getProperty("leggings.test.viewer", "none").equals("none")) {
                SkeletonChecks.passed("client");
                clientFinished = true;
                client.stop();
                return;
            }
            if (probe == null) {
                probe = System.getProperty("leggings.test.viewer").equals("jei") ? new JeiViewerProbe() : new ReiViewerProbe();
            }
            if (!probe.ready() || probe.cycles("mismatchedboots").size() != 1) {
                return;
            }
            probe.verifyCategories(false);
            require(probe.cycles("mismatchedthings").size() == 1 && probe.cycles("mismatchedleggings").isEmpty(),
                    "Boots-only shared category contains an uninstalled contribution");
            for (int attempt = 0; attempt < 3; attempt++) {
                probe.open("mismatchedboots");
                require(client.screen != null && probe.cycles("mismatchedthings").size() == 1,
                        "Boots-only repeated opening duplicated the shared example");
            }
            probe.verifyOutputTooltips("mismatchedboots");
            client.setScreen(null);
            if (state == 2) {
                SkeletonChecks.passed("client");
                clientFinished = true;
                client.stop();
                return;
            }
            state = 1;
            client.disconnectFromWorld(Component.literal("Boots-only viewer fixture"));
            client.setScreen(new TitleScreen());
        } catch (Throwable failure) {
            clientFinished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }
}
