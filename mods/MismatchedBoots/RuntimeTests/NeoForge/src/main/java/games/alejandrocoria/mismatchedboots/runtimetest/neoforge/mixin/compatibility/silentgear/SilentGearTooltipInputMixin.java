package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTooltipClientTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Controlled window polling during captures, not a replacement for native keybinding logic. */
@Mixin(InputConstants.class)
abstract class SilentGearTooltipInputMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void captureKey(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        Boolean pressed = SilentGearTooltipClientTest.key(key);
        if (pressed != null) {
            callback.setReturnValue(pressed);
        }
    }
}
