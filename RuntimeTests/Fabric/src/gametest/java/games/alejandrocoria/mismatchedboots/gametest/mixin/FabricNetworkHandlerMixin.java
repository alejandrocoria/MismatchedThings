package games.alejandrocoria.mismatchedboots.gametest.mixin;

import commonnetwork.networking.FabricNetworkHandler;
import games.alejandrocoria.mismatchedboots.gametest.NetworkTrafficProbe;
import games.alejandrocoria.mismatchedboots.network.AttributeRulesPayload;
import games.alejandrocoria.mismatchedboots.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedboots.network.ComponentRulesPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricNetworkHandler.class, remap = false)
abstract class FabricNetworkHandlerMixin {
    @Inject(method = "sendToServer", at = @At("HEAD"))
    private void mismatchedboots$recordClientReady(
            CustomPacketPayload payload, boolean ignoreCheck, CallbackInfo callback) {
        if (payload instanceof ClientReadyPayload) {
            NetworkTrafficProbe.recordClientReady(ignoreCheck);
        }
    }

    @Inject(method = "sendToClient", at = @At("HEAD"))
    private void mismatchedboots$recordAttributeRules(
            CustomPacketPayload payload, ServerPlayer player, boolean ignoreCheck, CallbackInfo callback) {
        if (payload instanceof AttributeRulesPayload rules) {
            NetworkTrafficProbe.recordAttributeRules(rules, ignoreCheck);
        } else if (payload instanceof ComponentRulesPayload rules) {
            NetworkTrafficProbe.recordComponentRules(rules, ignoreCheck);
        }
    }
}
