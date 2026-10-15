package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireArmorWearFaults;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility$LiveHandler", remap = false)
abstract class JustDireArmorWearDebitMixin {
    @ModifyReturnValue(method = "extract", at = @At("RETURN"))
    private int fault(int receipt, int amount, TransactionContext transaction) {
        boolean staged = JustDireArmorWearFaults.fault.equals("staged") && receipt > 0;
        int reported = JustDireArmorWearFaults.extracted(receipt);
        if (staged && ((EnergyHandler) (Object) this).extract(1, transaction) != 1) { throw new AssertionError("Real extra staged debit failed"); }
        return reported;
    }
}
