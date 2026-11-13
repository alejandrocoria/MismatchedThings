package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.WearSpikeCandidate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonHooks.class)
abstract class WearSpikeStandaloneMixin {
    @Unique
    private static boolean leggingswearspike$wearReady() {
        return false;
    }

    @WrapOperation(method = "lambda$onArmorHurt$0", require = 0, expect = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void leggingswearspike$wear(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                                              Operation<Void> original, LivingEntity capturedEntity, EquipmentSlot capturedSlot,
                                              ArmorHurtEvent.ArmorEntry entry) {
        WearSpikeCandidate.route(leggingswearspike$wearReady(), root, amount, entity, slot, original, entry.originalDamage);
    }
}
