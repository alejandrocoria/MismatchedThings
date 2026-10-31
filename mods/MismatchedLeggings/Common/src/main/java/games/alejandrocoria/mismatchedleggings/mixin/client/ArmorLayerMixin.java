package games.alejandrocoria.mismatchedleggings.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
abstract class ArmorLayerMixin<S extends HumanoidRenderState> {
    @Inject(method = "shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void mismatchedleggings$keepFallbackCandidate(ItemStack stack, EquipmentSlot slot,
                                                                 CallbackInfoReturnable<Boolean> callback) {
        // Native render-state extraction filters out missing assets before renderArmorPiece.
        // A valid pair still needs to reach our owner/neutral fallback, without changing its data.
        if (slot == EquipmentSlot.LEGS && stack.getItem() instanceof MixedLeggingsItem
                && MixedLeggingsStructure.validData(stack).isPresent()) {
            callback.setReturnValue(true);
        }
    }

    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Invoker("getArmorModel")
    protected abstract HumanoidModel<S> mismatchedleggings$getModel(S state, EquipmentSlot slot);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$render(PoseStack pose, SubmitNodeCollector collector, ItemStack stack,
                                      EquipmentSlot slot, int light, S state, CallbackInfo callback) {
        if (slot != EquipmentSlot.LEGS || !(stack.getItem() instanceof MixedLeggingsItem)) {
            return;
        }
        HumanoidModel<S> model = mismatchedleggings$getModel(state, slot);
        EquipmentClientInfo.LayerType layer = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
        MixedLeggingsEquipmentRenderer.render(equipmentRenderer, model, state, layer, stack,
                pose, collector, light, state.outlineColor);
        callback.cancel();
    }
}
