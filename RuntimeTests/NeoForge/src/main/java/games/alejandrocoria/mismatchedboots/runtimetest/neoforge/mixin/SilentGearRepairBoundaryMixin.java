package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearStateSpike;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only an explicitly scoped repair carrier is routed; ordinary native calls are untouched. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.util.GearHelper", remap = false)
abstract class SilentGearRepairBoundaryMixin {
    @Inject(method = "attemptDamage(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private static void repairRoot(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (SilentGearStateSpike.repair(stack, amount, entity)) { ci.cancel(); }
    }
}
