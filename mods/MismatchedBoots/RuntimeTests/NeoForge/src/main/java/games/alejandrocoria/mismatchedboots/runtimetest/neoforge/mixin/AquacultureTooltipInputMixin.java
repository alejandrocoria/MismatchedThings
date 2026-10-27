package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AquacultureTooltipClientTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Controls the native physical-key query only inside this fixture's synchronous captures. */
@Mixin(InputConstants.class)
abstract class AquacultureTooltipInputMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void aquacultureKey(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        Boolean pressed = AquacultureTooltipClientTest.key(key);
        if (pressed != null) {
            callback.setReturnValue(pressed);
        }
    }
}
