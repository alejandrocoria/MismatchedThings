package games.alejandrocoria.mismatchedboots.runtimetest.viewer.mixin;

import games.alejandrocoria.mismatchedboots.network.CompositionRules;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.viewer.RulesDeliveryGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompositionRulesClientLifecycle.class, remap = false)
abstract class RulesDeliveryMixin {
    @Inject(method = "install", at = @At("HEAD"), cancellable = true)
    private static void mismatchedboots$hold(CompositionRules rules, CallbackInfo callback) {
        if (RulesDeliveryGate.holdRules(rules)) {
            callback.cancel();
        }
    }
}
