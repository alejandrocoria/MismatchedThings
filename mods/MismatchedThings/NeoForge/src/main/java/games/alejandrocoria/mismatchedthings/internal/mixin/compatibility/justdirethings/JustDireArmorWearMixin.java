package games.alejandrocoria.mismatchedthings.internal.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings.JustDireArmorWearRouter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonHooks.class)
abstract class JustDireArmorWearMixin {
    @Unique
    private static boolean mismatchedthings$justDireArmorWearReady() {
        return false;
    }

    @WrapOperation(method = "lambda$onArmorHurt$0", require = 0, expect = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void mismatchedthings$justDireArmorWear(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                                                          Operation<Void> ordinary, LivingEntity capturedEntity, EquipmentSlot capturedSlot,
                                                          ArmorHurtEvent.ArmorEntry entry) {
        // Capture target arguments directly: no mutable LocalRef bridge is needed for this read-only entry.
        JustDireArmorWearRouter.route(mismatchedthings$justDireArmorWearReady(), root, amount, entity, slot, ordinary, entry.originalDamage);
    }
}
