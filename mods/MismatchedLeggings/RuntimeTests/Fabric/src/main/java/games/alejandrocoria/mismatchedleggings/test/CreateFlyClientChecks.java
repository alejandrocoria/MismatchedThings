package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class CreateFlyClientChecks {
    private static int ticks;
    private static boolean finished;

    private CreateFlyClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1800, "Create Fly client startup timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                return;
            }
            SkeletonChecks.verify();
            // LocalPlayer reads input, not Entity's shift flag used by the dedicated mock player.
            var previousInput = client.player.input.keyPresses;
            try {
                client.player.input.keyPresses = new Input(false, false, false, false, false, true, false);
                CreateFlyChecks.equippedChecks(client.player);
            } finally {
                client.player.input.keyPresses = previousInput;
            }
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
