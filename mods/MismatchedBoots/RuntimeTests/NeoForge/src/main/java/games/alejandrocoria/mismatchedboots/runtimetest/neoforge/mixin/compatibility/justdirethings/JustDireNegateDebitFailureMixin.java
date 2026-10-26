package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireNegateGuardPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lie only about the result after the real provider mutated its transaction: production must abort it. */
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility$LiveHandler", remap = false)
abstract class JustDireNegateDebitFailureMixin {
    @ModifyReturnValue(method = "extract", at = @At("RETURN"))
    private int reject(int extracted) {
        if (JustDireNegateGuardPlugin.rejectDebit && extracted > 0) {
            JustDireNegateGuardPlugin.rejectedAmount = extracted;
            return extracted - 1;
        }
        return extracted;
    }
}
