package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.RecipeCraftedProbe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerRecipeCraftedProbeMixin {
    @Inject(method = "triggerRecipeCrafted", at = @At("HEAD"))
    private void mismatchedboots$recordRecipeCrafted(
            RecipeHolder<?> recipe, List<ItemStack> inputs, CallbackInfo callback) {
        RecipeCraftedProbe.record((ServerPlayer) (Object) this, recipe.id());
    }
}
