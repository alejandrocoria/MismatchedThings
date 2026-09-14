package games.alejandrocoria.mismatchedboots.crossloader.neoforge.mixin;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
abstract class MinecraftWindowMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void mismatchedboots$conditionallyShowWindow(long window) {
        if (!Boolean.getBoolean("mismatchedboots.test.hideClientWindow")) {
            GLFW.glfwShowWindow(window);
        }
    }
}
