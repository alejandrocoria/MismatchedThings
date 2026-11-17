package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedleggings.test.NeoVitaeHellforgedClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only override physical Shift during this fixture's synchronous tooltip calls. */
@Mixin(InputConstants.class)
abstract class NeoVitaeInputProbe {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void leggings$neoVitaeShift(Window window, int key, CallbackInfoReturnable<Boolean> result) {
        if (Boolean.getBoolean("leggings.test.neoVitaePresentation")) {
            Boolean pressed = NeoVitaeHellforgedClient.key(key);
            if (pressed != null) {
                result.setReturnValue(pressed);
            }
        }
    }
}
