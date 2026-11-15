package games.alejandrocoria.entryspike.mixin;

import games.alejandrocoria.entryspike.EntryChecks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
abstract class LayerProbeMixin {
    @Inject(method = "getArmorModel", at = @At("RETURN"))
    private void entrySpike$lookup(HumanoidRenderState state, EquipmentSlot slot,
                                    CallbackInfoReturnable<HumanoidModel<?>> callback) {
        if (EntryChecks.active && slot == EquipmentSlot.FEET) { EntryChecks.lookups++; }
    }
}
