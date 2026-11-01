package games.alejandrocoria.mismatchedleggings.mixin;

import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithingState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemCombinerMenu.class)
abstract class SmithingPickupMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$validatePickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> callback) {
        if (this instanceof LeggingsSmithingState state && !state.mismatchedleggings$validateSmithingPreview()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$validateShift(Player player, int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (slot == SmithingMenu.RESULT_SLOT && this instanceof LeggingsSmithingState state && !state.mismatchedleggings$validateSmithingPreview()) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
