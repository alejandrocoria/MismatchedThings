package games.alejandrocoria.leggingsspike.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.leggingsspike.Fixture;
import games.alejandrocoria.leggingsspike.PairRenderer;
import games.alejandrocoria.leggingsspike.SpikeClient;
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
abstract class ArmorLayerMixin<S extends HumanoidRenderState> {
    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Invoker("getArmorModel")
    protected abstract HumanoidModel<S> leggingsSpike$getModel(S state, EquipmentSlot slot);

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void leggingsSpike$render(PoseStack pose, SubmitNodeCollector collector, ItemStack stack,
                                      EquipmentSlot slot, int light, S state, CallbackInfo callback) {
        int scenario = Fixture.scenario(stack);
        if (slot != EquipmentSlot.LEGS || scenario < 0) {
            return;
        }
        HumanoidModel<S> model = leggingsSpike$getModel(state, slot);
        EquipmentClientInfo.LayerType layer = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
        SpikeClient.nativeRenderer = equipmentRenderer;
        ItemStack[] sources = Fixture.sources(scenario);
        if (!PairRenderer.render(equipmentRenderer, model, state, layer, sources[0], sources[1],
                pose, collector, light, state.outlineColor)) {
            throw new AssertionError("Supported GUI fixture was rejected");
        }
        callback.cancel();
    }
}
