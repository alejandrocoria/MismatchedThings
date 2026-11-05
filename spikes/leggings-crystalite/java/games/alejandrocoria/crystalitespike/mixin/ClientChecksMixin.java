package games.alejandrocoria.crystalitespike.mixin;

import games.alejandrocoria.crystalitespike.SpikeClient;
import games.alejandrocoria.mismatchedleggings.test.SkeletonClientChecks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkeletonClientChecks.class, remap = false)
abstract class ClientChecksMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void spike$tick(Minecraft client, CallbackInfo callback) {
        SpikeClient.tick(client);
        callback.cancel();
    }
}
