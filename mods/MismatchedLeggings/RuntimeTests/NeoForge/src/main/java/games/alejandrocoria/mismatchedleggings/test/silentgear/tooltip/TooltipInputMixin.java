package games.alejandrocoria.mismatchedleggings.test.silentgear.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
abstract class TooltipInputMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void captureKey(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        Boolean pressed = TooltipClient.key(key);
        if (pressed != null) {
            callback.setReturnValue(pressed);
        }
    }
}
