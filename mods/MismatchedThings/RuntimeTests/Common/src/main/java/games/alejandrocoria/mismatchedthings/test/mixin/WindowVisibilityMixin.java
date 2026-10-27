package games.alejandrocoria.mismatchedthings.test.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hiding on the first tick is too late: startup can already expose the window. */
@Mixin(Window.class)
abstract class WindowVisibilityMixin {
    @Redirect(
            method = "createGlfwWindow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long mismatchedthings$createHiddenWindow(
            int width, int height, CharSequence title, long monitor, long share) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(width, height, title, monitor, share);
        if (window != 0 && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            throw new AssertionError("Automated client window was created visible");
        }
        return window;
    }
}
