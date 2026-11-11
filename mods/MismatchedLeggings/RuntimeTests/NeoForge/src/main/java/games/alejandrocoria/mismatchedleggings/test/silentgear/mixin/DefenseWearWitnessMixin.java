package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseBounceChecks;
import games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseLifecycle;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class DefenseWearWitnessMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"))
    private void witness(int amount, LivingEntity actor, EquipmentSlot slot, CallbackInfo callback) {
        DefenseBounceChecks.wear((ItemStack) (Object) this, amount, slot);
        DefenseLifecycle.wear((ItemStack) (Object) this, amount, slot);
    }
}
