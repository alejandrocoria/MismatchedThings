package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
abstract class ItemCombinerMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void mismatchedboots$clearSmithingRecipeOnClose(Player player, CallbackInfo callback) {
        if (this instanceof SmithingMenuRecipeState state) {
            state.mismatchedboots$clearSmithingState();
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$validatePickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> callback) {
        if (this instanceof SmithingMenuRecipeState state && !state.mismatchedboots$validateSmithingPreview()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$validateShift(Player player, int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (slot == SmithingMenu.RESULT_SLOT && this instanceof SmithingMenuRecipeState state
                && !state.mismatchedboots$validateSmithingPreview()) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
