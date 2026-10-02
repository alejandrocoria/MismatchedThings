package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

final class MismatchedWorkbenchJeiExamples {
    private static final MismatchedWorkbenchRecipe WORKBENCH_RECIPE = new MixedBootsWorkbenchRecipe();

    private MismatchedWorkbenchJeiExamples() {
    }

    static Optional<MismatchedWorkbenchJeiExample> create(IJeiRuntime runtime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }

        return MismatchedWorkbenchJeiCycle.create(collectBoots(runtime), (left, right) -> {
            MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(left, right);
            return WORKBENCH_RECIPE.matches(input, level)
                    ? WORKBENCH_RECIPE.assemblePair(input)
                    : MismatchedWorkbenchResult.EMPTY;
        });
    }

    private static List<ItemStack> collectBoots(IJeiRuntime runtime) {
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientVisibility visibility = runtime.getJeiHelpers().getIngredientVisibility();
        IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        return MismatchedWorkbenchJeiCycle.collectCandidates(
                ingredientManager.getAllItemStacks(),
                stack -> visibility.isIngredientVisible(VanillaTypes.ITEM_STACK, stack, UidContext.Recipe),
                stack -> helper.getUid(stack, UidContext.Recipe),
                stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
