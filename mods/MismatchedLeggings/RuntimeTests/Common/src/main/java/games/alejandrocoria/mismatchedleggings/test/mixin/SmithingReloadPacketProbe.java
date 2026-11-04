package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.SmithingReloadPackets;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class SmithingReloadPacketProbe {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("RETURN"))
    private void leggings$observeSmithingClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (Boolean.getBoolean("leggings.test.smithingReload")) {
            SmithingReloadPackets.capture(player, packet.containerId(), packet.slotNum());
        }
    }
}
