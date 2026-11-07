package games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin;

import games.alejandrocoria.mismatchedleggings.test.SkeletonClientNeoForge;
import games.alejandrocoria.mismatchedleggings.test.rootsclassic.RootsRenderingSpike;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkeletonClientNeoForge.class, remap = false)
abstract class SpikeBootstrap {
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private static void rootsSpike$setup(FMLClientSetupEvent event, CallbackInfo callback) {
        if (Boolean.getBoolean("leggings.test.rootsSpike")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> RootsRenderingSpike.tick(Minecraft.getInstance()));
            callback.cancel();
        }
    }
}
