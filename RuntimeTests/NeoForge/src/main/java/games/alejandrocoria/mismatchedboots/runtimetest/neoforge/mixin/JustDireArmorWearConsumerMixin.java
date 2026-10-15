package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireArmorWearCandidate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonHooks.class)
abstract class JustDireArmorWearConsumerMixin {
    @WrapOperation(method = "lambda$onArmorHurt$0", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void consume(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot, Operation<Void> original,
                                @Local(argsOnly = true) ArmorHurtEvent.ArmorEntry entry) {
        if (!JustDireArmorWearCandidate.consume(stack, amount, entity, slot, entry.originalDamage)) { original.call(stack, amount, entity, slot); }
    }
}
