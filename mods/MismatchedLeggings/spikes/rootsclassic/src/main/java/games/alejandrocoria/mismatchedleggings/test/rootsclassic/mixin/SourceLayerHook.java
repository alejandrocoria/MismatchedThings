package games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.test.rootsclassic.RootsRenderingSpike;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changes one mixed-source request, never the original/native armor-layer route. */
@Mixin(value = MixedLeggingsEquipmentRenderer.class, remap = false)
abstract class SourceLayerHook {
    @WrapOperation(method = "capture", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers("
                    + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;"
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private static void rootsSpike$layer(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType type,
                                          ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack source,
                                          PoseStack pose, SubmitNodeCollector collector, int light, int outline, Operation<Void> original) {
        if (RootsRenderingSpike.enabled && RootsRenderingSpike.roots(source)) {
            type = EquipmentClientInfo.LayerType.HUMANOID;
        }
        original.call(renderer, type, asset, model, state, source, pose, collector, light, outline);
    }
}
