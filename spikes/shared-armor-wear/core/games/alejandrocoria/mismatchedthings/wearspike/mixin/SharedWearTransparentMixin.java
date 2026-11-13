package games.alejandrocoria.mismatchedthings.wearspike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CommonHooks.class, priority = 2000)
abstract class SharedWearTransparentMixin {
    @WrapOperation(method = "lambda$onArmorHurt$0", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void forward(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot, Operation<Void> original) {
        original.call(root, amount, entity, slot);
    }
}
