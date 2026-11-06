package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedleggings.test.ForbiddenModifierPresentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
abstract class ForbiddenInputProbe {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void leggings$shift(Window window, int key, CallbackInfoReturnable<Boolean> result) {
        Boolean pressed = ForbiddenModifierPresentation.key(key);
        if (pressed != null) { result.setReturnValue(pressed); }
    }
}
