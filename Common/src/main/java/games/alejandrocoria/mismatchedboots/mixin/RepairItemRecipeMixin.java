package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
abstract class RepairItemRecipeMixin {
    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    private static void mismatchedboots$rejectMixedBoots(
            ItemStack first,
            ItemStack second,
            CallbackInfoReturnable<Boolean> callback) {
        if (ModContent.isMixedBoots(first) || ModContent.isMixedBoots(second)) {
            callback.setReturnValue(false);
        }
    }
}
