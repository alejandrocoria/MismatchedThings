package games.alejandrocoria.crystalitespike.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.crystalitespike.CrystaliteBridge;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MixedLeggingsEquipmentRenderer.class, remap = false)
abstract class MixedRendererMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void spike$render(EquipmentLayerRenderer renderer, Model<?> model, Object state,
                                     EquipmentClientInfo.LayerType layer, ItemStack root, PoseStack pose,
                                     SubmitNodeCollector output, int light, int outline, CallbackInfo callback) {
        CrystaliteBridge.equipmentRenderer = renderer;
        if (model instanceof HumanoidModel<?> humanoid && state instanceof HumanoidRenderState humanoidState
                && CrystaliteBridge.render(renderer, (HumanoidModel<HumanoidRenderState>) humanoid,
                humanoidState, layer, root, pose, output, light, outline)) {
            callback.cancel();
        }
    }
}
