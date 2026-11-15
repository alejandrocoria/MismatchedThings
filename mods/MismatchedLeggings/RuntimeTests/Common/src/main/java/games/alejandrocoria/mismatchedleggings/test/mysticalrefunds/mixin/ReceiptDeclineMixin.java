package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A registered product declines its claim; neither shared dispatch nor the sibling is replaced. */
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalRefundCompatibility", remap = false)
abstract class ReceiptDeclineMixin {
    @Inject(method = "capture", at = @At("HEAD"), cancellable = true)
    private static void refundTest$decline(CallbackInfoReturnable<Object> info) {
        info.setReturnValue(null);
    }
}
