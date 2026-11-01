package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.SmithingCredits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerPlayer.class)
abstract class SmithingCreditsMixin {
    @Inject(method = "triggerRecipeCrafted", at = @At("HEAD"))
    private void leggings$observeCredits(RecipeHolder<?> recipe, List<ItemStack> inputs, CallbackInfo callback) {
        SmithingCredits.crafted((ServerPlayer) (Object) this, recipe);
    }
}
