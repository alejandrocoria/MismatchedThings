package games.alejandrocoria.mismatchedboots.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin {
    @Redirect(
            method = "renderArmorPiece",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers"
                            + "(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
                            + "Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private <S> void mismatchedboots$renderMixedFeet(
            EquipmentLayerRenderer renderer,
            EquipmentClientInfo.LayerType layerType,
            ResourceKey<EquipmentAsset> assetId,
            Model<? super S> model,
            S state,
            ItemStack stack,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int outlineColor) {
        if (!MixedBootsEquipmentRenderer.renderLayers(
                renderer,
                layerType,
                model,
                state,
                stack,
                poseStack,
                submitNodeCollector,
                lightCoords,
                outlineColor)) {
            renderer.renderLayers(
                    layerType,
                    assetId,
                    model,
                    state,
                    stack,
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    outlineColor);
        }
    }
}
