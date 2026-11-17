package games.alejandrocoria.mismatchedleggings.test.neovitaeupdate;

import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
abstract class RendererMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void research$observe(CallbackInfo callback) {
        if (Boolean.getBoolean("nv.spike")) { EquippedClient.renderer = (EquipmentLayerRenderer) (Object) this; }
    }
}
