package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireArmorWearCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireArmorWearFaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JustDireArmorWearCompatibility.class)
abstract class JustDireArmorWearCommitMixin {
    @Inject(method = "consume", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/transfer/transaction/Transaction;commit()V", shift = At.Shift.AFTER))
    private static void committed(CallbackInfoReturnable<Boolean> callback) {
        if (JustDireArmorWearFaults.fault.equals("committed")) {
            JustDireArmorWearFaults.fault = "";
            throw new JustDireArmorWearFaults.CommittedFailure();
        }
    }
}
