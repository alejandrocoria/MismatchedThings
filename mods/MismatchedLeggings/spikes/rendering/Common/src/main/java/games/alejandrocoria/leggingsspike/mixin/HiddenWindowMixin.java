package games.alejandrocoria.leggingsspike.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only: hiding on the first tick would already expose the startup window. */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @Redirect(method = "createGlfwWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long leggingsSpike$createHidden(int width, int height, CharSequence title, long monitor, long share) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(width, height, title, monitor, share);
        if (window != 0 && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            throw new AssertionError("Spike window was created visible");
        }
        return window;
    }
}
