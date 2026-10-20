package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearBounceJointTest;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearBounceTest;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe production payment, not a parallel candidate writer or a native-code permission grant. */
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefense", remap = false)
abstract class SilentGearDefenseWearWitnessMixin {
    @WrapOperation(method = "allowNativeFallWear", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void payment(ItemStack root, int amount, LivingEntity actor, EquipmentSlot slot, Operation<Void> original) {
        SilentGearBounceJointTest.sgWear();
        SilentGearBounceTest.wearCalls++; SilentGearBounceTest.requestedWear = amount; original.call(root, amount, actor, slot);
    }
}
