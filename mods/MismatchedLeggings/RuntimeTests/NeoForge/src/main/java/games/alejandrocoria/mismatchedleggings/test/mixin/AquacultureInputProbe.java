package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedleggings.test.AquacultureTooltipChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Override physical Shift input only during this profile's synchronous native tooltip captures. */
@Mixin(InputConstants.class)
abstract class AquacultureInputProbe {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void leggings$aquacultureShift(Window window, int key, CallbackInfoReturnable<Boolean> result) {
        if (Boolean.getBoolean("leggings.test.aquaculture")) {
            Boolean pressed = AquacultureTooltipChecks.key(key);
            if (pressed != null) {
                result.setReturnValue(pressed);
            }
        }
    }
}
