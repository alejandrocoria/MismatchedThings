package games.alejandrocoria.mismatchedleggings.mixin;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
abstract class RepairItemRecipeMixin {
    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    private static void mismatchedleggings$rejectMixedPair(ItemStack first, ItemStack second,
                                                          CallbackInfoReturnable<Boolean> callback) {
        // Vanilla creates a bare item and would discard both provenance and live components.
        if (MixedLeggingsStructure.isMixedItem(first) || MixedLeggingsStructure.isMixedItem(second)) {
            callback.setReturnValue(false);
        }
    }
}
