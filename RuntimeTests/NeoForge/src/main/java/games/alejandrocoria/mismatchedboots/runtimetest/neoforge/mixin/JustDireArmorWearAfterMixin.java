package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireArmorWearFaults;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CommonHooks.class, priority = 600)
abstract class JustDireArmorWearAfterMixin {
    @WrapOperation(method = "lambda$onArmorHurt$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void forward(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot, Operation<Void> original) {
        JustDireArmorWearFaults.before();
        original.call(stack, amount, entity, slot);
        JustDireArmorWearFaults.after();
    }
}

