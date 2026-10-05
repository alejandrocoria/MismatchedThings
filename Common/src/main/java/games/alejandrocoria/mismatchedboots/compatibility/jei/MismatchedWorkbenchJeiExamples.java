package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
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
    private MismatchedWorkbenchJeiExamples() {
    }

    static Optional<MismatchedWorkbenchJeiExample> create(IJeiRuntime runtime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }

        var attributes = AttributeRuleState.current();
        var components = ComponentRuleState.current();
        return MismatchedWorkbenchJeiCycle.create(collectBoots(runtime), (left, right) ->
                MismatchedWorkbenchClientRecipes.assemblePair(
                        new MismatchedWorkbenchInput(left, right), level, attributes, components));
    }

    private static List<ItemStack> collectBoots(IJeiRuntime runtime) {
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientVisibility visibility = runtime.getJeiHelpers().getIngredientVisibility();
        IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
        return MismatchedWorkbenchJeiCycle.collectCandidates(
                ingredientManager.getAllItemStacks(),
                stack -> visibility.isIngredientVisible(VanillaTypes.ITEM_STACK, stack),
                stack -> helper.getUid(stack, UidContext.Recipe),
                stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
