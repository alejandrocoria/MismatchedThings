package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class BetterNetherClientChecks {
    private static int ticks;
    private static boolean finished;

    private BetterNetherClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1800, "Better Nether client startup timed out: screen=" + client.screen
                    + ", world=" + client.level + ", overlay=" + client.getOverlay());
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                return;
            }
            BetterNetherChecks.equippedChecks(client.player, client.level.registryAccess());
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }
}
