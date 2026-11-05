package games.alejandrocoria.mismatchedleggings.test.fabricmixin;

import games.alejandrocoria.mismatchedleggings.test.CrystaliteFailureControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.client.render.BufferedEquipment", remap = false)
abstract class CrystaliteSplitProbe {
    @Inject(method = "prepareSplit", at = @At("RETURN"), cancellable = true)
    private void leggingsTest$rejectSecondHalf(boolean left, CallbackInfoReturnable<Boolean> callback) {
        if (CrystaliteFailureControls.failRightSplit && callback.getReturnValueZ()) {
            if (left) {
                CrystaliteFailureControls.preparedLeft++;
            } else {
                CrystaliteFailureControls.rejectedRight++;
                callback.setReturnValue(false);
            }
        }
    }
}
