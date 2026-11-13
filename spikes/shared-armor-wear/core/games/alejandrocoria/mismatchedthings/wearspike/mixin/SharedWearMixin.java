package games.alejandrocoria.mismatchedthings.wearspike.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedthings.wearspike.SharedWearRouter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonHooks.class)
abstract class SharedWearMixin {
    @Unique
    private static boolean mismatchedthings$wearReady() {
        return false;
    }

    @WrapOperation(method = "lambda$onArmorHurt$0", require = 0, expect = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void mismatchedthings$wear(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                                             Operation<Void> ordinary, LivingEntity capturedEntity, EquipmentSlot capturedSlot,
                                             ArmorHurtEvent.ArmorEntry entry) {
        SharedWearRouter.route(mismatchedthings$wearReady(), root, amount, entity, slot, ordinary, entry.originalDamage);
    }
}
