package games.alejandrocoria.mismatchedboots.gametest.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
abstract class MinecraftWindowMixin {
    private static final String HIDE_WINDOW_PROPERTY = "mismatchedboots.test.hideClientWindow";

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void mismatchedboots$conditionallyShowWindow(long window) {
        if (!Boolean.getBoolean(HIDE_WINDOW_PROPERTY)) {
            GLFW.glfwShowWindow(window);
        }
    }
}
