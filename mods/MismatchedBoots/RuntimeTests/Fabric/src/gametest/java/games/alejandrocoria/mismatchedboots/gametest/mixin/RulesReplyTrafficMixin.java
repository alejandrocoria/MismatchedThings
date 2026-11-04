package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.NetworkTrafficProbe;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedboots.network.FabricCompositionRulesNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FabricCompositionRulesNetwork.class, remap = false)
abstract class RulesReplyTrafficMixin {
    @Redirect(method = "sendRules", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private static void mismatchedboots$recordReply(ServerPlayer player, CustomPacketPayload payload) {
        NetworkTrafficProbe.recordRules((CompositionRulesPayload) payload);
        ServerPlayNetworking.send(player, payload);
    }
}
