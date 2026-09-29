package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsGrindstonePolicy;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsRepairPolicy;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$rejectUnsupportedMixedInputs(
            ItemStack input,
            ItemStack additional,
            CallbackInfoReturnable<ItemStack> callback) {
        if (MixedBootsGrindstonePolicy.rejects(input, additional)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }

    @WrapOperation(
            method = "computeResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/GrindstoneMenu;mergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mismatchedboots$orientAndRepair(
            GrindstoneMenu menu, ItemStack input, ItemStack additional, Operation<ItemStack> original) {
        ItemStack mixed = MixedBootsRepairPolicy.canUseOrdinaryDonor(input, additional) ? input : additional;
        ItemStack donor = mixed == input ? additional : input;
        if (!canRepair(mixed, donor)) {
            return original.call(menu, input, additional);
        }
        ItemStack result = original.call(menu, mixed, donor);
        return MixedBootsRepairPolicy.restoreDurability(result, mixed, donor);
    }

    @ModifyExpressionValue(
            method = "mergeItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean mismatchedboots$acceptSourceDonor(
            boolean vanillaMatch,
            @Local(argsOnly = true, ordinal = 0) ItemStack input,
            @Local(argsOnly = true, ordinal = 1) ItemStack additional) {
        return vanillaMatch || canRepair(input, additional);
    }

    private static boolean canRepair(ItemStack mixed, ItemStack donor) {
        return MixedBootsRepairPolicy.canUseOrdinaryDonor(mixed, donor)
                && Services.PLATFORM.canCombineRepair(mixed)
                && Services.PLATFORM.canCombineRepair(donor);
    }
}
