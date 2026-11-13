package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedleggings.test.JustDireArmorWearChecks;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Faults operate on the real retained handler and staged transaction, never a replacement wallet. */
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireResources$LiveHandler", remap = false)
abstract class JustDireArmorWearDebitProbe {
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int shortTransfer(int amount) {
        return JustDireArmorWearChecks.fault.equals("short") ? amount - 1 : amount;
    }

    @ModifyReturnValue(method = "extract", at = @At("RETURN"), require = 1)
    private int afterStaging(int receipt, int amount, TransactionContext transaction) {
        boolean extra = JustDireArmorWearChecks.fault.equals("staged") && receipt > 0;
        JustDireArmorWearChecks.extracted(receipt);
        if (extra && ((EnergyHandler) (Object) this).extract(1, transaction) != 1) {
            throw new AssertionError("Extra real staged debit failed");
        }
        return receipt;
    }
}
