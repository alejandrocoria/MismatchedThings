package games.alejandrocoria.mismatchedleggings.spike.aether.mixin;

import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
abstract class RendererProbe {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void aetherSpike$renderer(CallbackInfo ci) {
        EquippedClient.renderer = (EquipmentLayerRenderer) (Object) this;
    }
}
