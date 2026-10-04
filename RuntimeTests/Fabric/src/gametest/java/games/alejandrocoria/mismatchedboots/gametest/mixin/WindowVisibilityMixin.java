package games.alejandrocoria.mismatchedboots.gametest.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
abstract class WindowVisibilityMixin {
    private static final String HIDE_WINDOW_PROPERTY = "mismatchedboots.test.hideClientWindow";

    @Redirect(
            method = "createGlfwWindow",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long mismatchedboots$conditionallyHideWindow(
            int width, int height, CharSequence title, long monitor, long share) {
        if (Boolean.getBoolean(HIDE_WINDOW_PROPERTY)) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
        return GLFW.glfwCreateWindow(width, height, title, monitor, share);
    }
}
