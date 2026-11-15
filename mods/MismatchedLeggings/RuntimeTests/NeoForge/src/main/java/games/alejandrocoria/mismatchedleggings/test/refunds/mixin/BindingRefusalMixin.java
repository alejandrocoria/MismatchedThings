package games.alejandrocoria.mismatchedleggings.test.refunds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireRefundCompatibility", remap = false)
abstract class BindingRefusalMixin {
    @Inject(method = "bindItems", at = @At("HEAD"), cancellable = true)
    private static void refundTest$unbound(CallbackInfo info) {
        info.cancel();
    }
}
