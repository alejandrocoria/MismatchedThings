package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.client.network.FabricCompositionRulesClientNetwork;
import games.alejandrocoria.mismatchedboots.gametest.NetworkTrafficProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricCompositionRulesClientNetwork.class, remap = false)
abstract class RulesReadyTrafficMixin {
    @Inject(method = "sendReady", at = @At("HEAD"))
    private static void mismatchedboots$recordReady(CallbackInfo callback) {
        NetworkTrafficProbe.recordClientReady();
    }
}
