package games.alejandrocoria.mismatchedleggings.test.neovitaeupdate;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import games.alejandrocoria.mismatchedleggings.test.NeoVitaeUpdateClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
abstract class InputMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void research$key(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        Boolean selected = NeoVitaeUpdateClient.key(key);
        if (selected != null) { callback.setReturnValue(selected); }
    }
}
