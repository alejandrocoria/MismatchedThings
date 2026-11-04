package games.alejandrocoria.rulesspike.mixin;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.rulesspike.Proof;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompositionRulesClientLifecycle.class)
abstract class LegacyClientMixin {
    @Inject(method = {"onConnected()V", "onDisconnected()V"}, at = @At("HEAD"), cancellable = true)
    private static void skipLegacy(CallbackInfo callback) {
        if (Proof.CANDIDATE) {
            callback.cancel();
        }
    }
}
