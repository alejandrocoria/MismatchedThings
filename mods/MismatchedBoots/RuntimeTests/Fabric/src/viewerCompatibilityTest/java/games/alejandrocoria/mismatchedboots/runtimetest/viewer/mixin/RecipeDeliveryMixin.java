package games.alejandrocoria.mismatchedboots.runtimetest.viewer.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.runtimetest.viewer.RulesDeliveryGate;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(value = MismatchedWorkbenchClientRecipes.class, remap = false)
abstract class RecipeDeliveryMixin {
    @Inject(method = "install", at = @At("HEAD"), cancellable = true)
    private static void mismatchedboots$hold(Collection<RecipeHolder<WorkbenchRecipe>> recipes, CallbackInfo callback) {
        if (RulesDeliveryGate.holdRecipes(recipes)) {
            callback.cancel();
        }
    }
}
