package games.alejandrocoria.mismatchedboots.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState> {
    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Invoker("getArmorModel")
    protected abstract HumanoidModel<S> mismatchedboots$getModel(S state, EquipmentSlot slot);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$renderMixedFeet(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ItemStack stack,
            EquipmentSlot slot,
            int lightCoords,
            S state,
            CallbackInfo callback) {
        if (slot != EquipmentSlot.FEET || !(stack.getItem() instanceof MixedBootsItem)
                || !HumanoidArmorLayer.shouldRender(stack, slot)) {
            return;
        }

        HumanoidModel<S> model = mismatchedboots$getModel(state, slot);
        EquipmentClientInfo.LayerType layerType = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                                                 ? EquipmentClientInfo.LayerType.HUMANOID_BABY
                                                 : EquipmentClientInfo.LayerType.HUMANOID;
        if (MixedBootsEquipmentRenderer.renderLayers(
                equipmentRenderer,
                layerType,
                model,
                state,
                stack,
                poseStack,
                submitNodeCollector,
                lightCoords,
                state.outlineColor)) {
            callback.cancel();
        }
        // A declined split keeps native owner rendering. Its second model-set lookup is cheap;
        // source extensions still resolve only in the single native fallback draw.
    }
}
