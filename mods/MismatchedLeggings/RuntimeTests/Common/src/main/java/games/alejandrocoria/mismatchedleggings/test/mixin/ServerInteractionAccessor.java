package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerInteractionAccessor {
    @Accessor("awaitingPositionFromClient")
    Vec3 leggings$pendingTeleport();
}
