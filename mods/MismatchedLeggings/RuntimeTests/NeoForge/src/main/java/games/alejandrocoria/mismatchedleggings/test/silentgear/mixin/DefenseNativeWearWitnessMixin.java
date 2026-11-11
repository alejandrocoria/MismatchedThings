package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseBounceChecks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentchaos512.gear.util.GearHelper", remap = false)
abstract class DefenseNativeWearWitnessMixin {
    @Inject(method = "attemptDamage(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"))
    private static void witness(ItemStack stack, int amount, LivingEntity actor, EquipmentSlot slot, CallbackInfo callback) {
        DefenseBounceChecks.nativeWear(stack, amount, slot);
    }
}
