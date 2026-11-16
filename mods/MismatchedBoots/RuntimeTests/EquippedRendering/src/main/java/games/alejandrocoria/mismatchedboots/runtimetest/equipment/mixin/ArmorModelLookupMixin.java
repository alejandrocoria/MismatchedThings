package games.alejandrocoria.mismatchedboots.runtimetest.equipment.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.equipment.ArmorLayerEntryChecks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
abstract class ArmorModelLookupMixin {
    @Inject(method = "getArmorModel", at = @At("RETURN"))
    private void mismatchedboots$recordLookup(HumanoidRenderState state, EquipmentSlot slot,
                                               CallbackInfoReturnable<HumanoidModel<?>> callback) {
        ArmorLayerEntryChecks.recordLookup(slot, callback.getReturnValue());
    }
}
