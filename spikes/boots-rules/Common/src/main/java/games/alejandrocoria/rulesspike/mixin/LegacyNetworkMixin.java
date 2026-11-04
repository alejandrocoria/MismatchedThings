package games.alejandrocoria.rulesspike.mixin;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesNetwork;
import games.alejandrocoria.rulesspike.Proof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the rules transport in the candidate, never the frozen legacy peer. */
@Mixin(CompositionRulesNetwork.class)
abstract class LegacyNetworkMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void skipLegacy(CallbackInfo callback) {
        if (Proof.CANDIDATE) {
            callback.cancel();
        }
    }
}
