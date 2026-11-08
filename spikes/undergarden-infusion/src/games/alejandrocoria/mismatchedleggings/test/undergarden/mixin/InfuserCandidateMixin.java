package games.alejandrocoria.mismatchedleggings.test.undergarden.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quek.undergarden.recipe.InfusingRecipe;

/** One native ingredient query only; recipe slot, copy, time and payment remain native. */
@Mixin(InfusingRecipe.class)
public interface InfuserCandidateMixin {
    @WrapOperation(method = "matches(Lquek/undergarden/recipe/InfuserInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Ingredient;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean spike$admit(Ingredient ingredient, ItemStack stack, Operation<Boolean> original) {
        return original.call(ingredient, stack) || InfusionSpike.admit((InfusingRecipe) this, stack);
    }
}
