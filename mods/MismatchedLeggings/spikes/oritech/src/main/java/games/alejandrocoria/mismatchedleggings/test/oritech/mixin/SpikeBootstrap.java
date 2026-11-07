package games.alejandrocoria.mismatchedleggings.test.oritech.mixin;

import games.alejandrocoria.mismatchedleggings.test.SkeletonClientNeoForge;
import games.alejandrocoria.mismatchedleggings.test.oritech.OritechSpike;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkeletonClientNeoForge.class)
abstract class SpikeBootstrap {
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private static void start(FMLClientSetupEvent event, CallbackInfo callback) {
        if (Boolean.getBoolean("leggings.test.oritechSpike")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> OritechSpike.tick(Minecraft.getInstance()));
            callback.cancel();
        }
    }
}
