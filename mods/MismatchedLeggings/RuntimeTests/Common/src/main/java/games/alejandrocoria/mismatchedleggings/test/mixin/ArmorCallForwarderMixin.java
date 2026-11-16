package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedEntryChecks;
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

/** Transparent competing wrapper: no priority selection, argument changes or replacement draw. */
@Mixin(HumanoidArmorLayer.class)
abstract class ArmorCallForwarderMixin {
    @WrapOperation(method = "renderArmorPiece", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers("
                    + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;"
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private <S> void leggingsTest$forwardArmor(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType layer,
                                               ResourceKey<EquipmentAsset> asset, Model<? super S> model, S state,
                                               ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
                                               int light, int outline, Operation<Void> original) {
        EquippedEntryChecks.recordForward(stack);
        original.call(renderer, layer, asset, model, state, stack, pose, collector, light, outline);
    }
}
