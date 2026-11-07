package games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidArmorLayer.class)
public interface NativeArmorAccess {
    @Invoker("renderArmorPiece")
    void leggingsRootsTest$render(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot,
                          int light, HumanoidRenderState state);

    @Invoker("getArmorModel")
    HumanoidModel<?> leggingsRootsTest$model(HumanoidRenderState state, EquipmentSlot slot);
}
