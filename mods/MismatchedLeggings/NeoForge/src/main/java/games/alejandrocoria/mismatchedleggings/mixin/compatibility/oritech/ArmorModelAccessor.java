package games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidArmorLayer.class)
public interface ArmorModelAccessor {
    @Invoker("getArmorModel")
    HumanoidModel<?> mismatchedleggings$model(HumanoidRenderState state, EquipmentSlot slot);
}
