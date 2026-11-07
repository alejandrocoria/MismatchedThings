package games.alejandrocoria.mismatchedleggings.test.oritech.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.test.oritech.OritechSpike;
import games.alejandrocoria.mismatchedleggings.test.oritech.NativeExoBridge;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MixedLeggingsEquipmentRenderer.class)
abstract class SpikeRenderHook {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void render(EquipmentLayerRenderer renderer, Model<?> model, Object state,
                               EquipmentClientInfo.LayerType layer, ItemStack root, PoseStack pose,
                               SubmitNodeCollector destination, int light, int outline, CallbackInfo callback) {
        if (OritechSpike.enabled && NativeExoBridge.mixed(renderer, model, state, root, pose, destination, light, outline)) {
            callback.cancel();
        }
    }
}
