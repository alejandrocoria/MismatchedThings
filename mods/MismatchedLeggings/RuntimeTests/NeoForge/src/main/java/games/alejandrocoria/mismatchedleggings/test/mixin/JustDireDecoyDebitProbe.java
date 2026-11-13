package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.JustDireDecoyChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Force a real staged partial transfer; rollback is still the production transaction's job. */
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireResources$LiveHandler")
public abstract class JustDireDecoyDebitProbe {
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private int partial(int amount) {
        return JustDireDecoyChecks.partialDebit ? Math.max(0, amount - 1) : amount;
    }
}
