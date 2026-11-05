package games.alejandrocoria.mismatchedleggings.test.fabricmixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Controlled window-key input, like Fabric's client GameTests; never fake Create's modifier output. */
@Mixin(InputConstants.class)
abstract class CreateFlyInputProbe {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void leggings$tooltipShift(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.getBoolean("leggings.test.createFly")
                && (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            Boolean shifted = games.alejandrocoria.mismatchedleggings.test.CreateFlyTooltipChecks.shiftDown;
            if (shifted != null) {
                callback.setReturnValue(shifted);
            }
        }
    }
}
