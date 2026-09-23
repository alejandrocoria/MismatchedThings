package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCombinerMenu.class)
abstract class ItemCombinerMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void mismatchedboots$clearSmithingRecipeOnClose(Player player, CallbackInfo callback) {
        if (this instanceof SmithingMenuRecipeState state) {
            state.mismatchedboots$clearAdditionalRecipe();
        }
    }
}
