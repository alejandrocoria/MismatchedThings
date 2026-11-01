package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsGrindstone;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
abstract class GrindstoneMenuMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$rejectUnsupportedInputs(ItemStack first, ItemStack second,
                                                            CallbackInfoReturnable<ItemStack> callback) {
        if (LeggingsGrindstone.rejects(first, second)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }

    @WrapOperation(method = "computeResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/GrindstoneMenu;mergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mismatchedleggings$orientAndRepair(GrindstoneMenu menu, ItemStack first, ItemStack second,
                                                        Operation<ItemStack> original) {
        ItemStack mixed = LeggingsGrindstone.canRepair(first, second) ? first : second;
        ItemStack donor = mixed == first ? second : first;
        if (!LeggingsGrindstone.canRepair(mixed, donor)) {
            return original.call(menu, first, second);
        }
        return LeggingsGrindstone.restoreDurability(original.call(menu, mixed, donor), mixed, donor);
    }

    @ModifyExpressionValue(method = "mergeItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean mismatchedleggings$acceptSourceDonor(boolean nativeMatch,
                                                        @Local(argsOnly = true, ordinal = 0) ItemStack first,
                                                        @Local(argsOnly = true, ordinal = 1) ItemStack second) {
        return nativeMatch || LeggingsGrindstone.canRepair(first, second);
    }
}
