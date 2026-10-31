package games.alejandrocoria.mismatchedleggings.mixin;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native equipment paths trust EQUIPPABLE alone; reject only our invalid roots. */
@Mixin(LivingEntity.class)
abstract class LivingEntityEquipmentMixin {
    @Inject(method = "getEquipmentSlotForItem", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$invalidSlot(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> callback) {
        if (invalid(stack)) {
            callback.setReturnValue(EquipmentSlot.MAINHAND);
        }
    }

    @Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$invalidEquipment(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> callback) {
        if (invalid(stack) && slot.isArmor()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "canEquipWithDispenser", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$invalidDispense(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (invalid(stack)) {
            callback.setReturnValue(false);
        }
    }

    @Unique
    private static boolean invalid(ItemStack stack) {
        return MixedLeggingsStructure.isMixedItem(stack) && MixedLeggingsStructure.classify(stack) != MixedLeggingsStructure.State.VALID;
    }
}
