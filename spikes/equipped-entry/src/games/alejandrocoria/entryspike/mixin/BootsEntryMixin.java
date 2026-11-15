package games.alejandrocoria.entryspike.mixin;

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

/** Candidate only. Renderer, native fallback and late NeoForge adapters remain production code. */
@Mixin(value = HumanoidArmorLayer.class, priority = 1100)
abstract class BootsEntryMixin<S extends HumanoidRenderState> {
    @Shadow @Final private EquipmentLayerRenderer equipmentRenderer;

    @Invoker("getArmorModel")
    protected abstract HumanoidModel<S> entrySpike$model(S state, EquipmentSlot slot);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void entrySpike$boots(PoseStack pose, SubmitNodeCollector collector, ItemStack stack,
                                  EquipmentSlot slot, int light, S state, CallbackInfo callback) {
        if (slot != EquipmentSlot.FEET || !(stack.getItem() instanceof MixedBootsItem)
                || !HumanoidArmorLayer.shouldRender(stack, slot)) {
            return;
        }
        var model = entrySpike$model(state, slot);
        var layer = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID;
        if (MixedBootsEquipmentRenderer.renderLayers(equipmentRenderer, layer, model, state,
                stack, pose, collector, light, state.outlineColor)) {
            callback.cancel();
        }
    }
}
