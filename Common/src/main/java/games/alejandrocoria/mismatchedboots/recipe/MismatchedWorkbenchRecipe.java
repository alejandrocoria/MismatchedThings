package games.alejandrocoria.mismatchedboots.recipe;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeType;

public interface MismatchedWorkbenchRecipe extends Recipe<MismatchedWorkbenchInput> {
    RecipeType<MismatchedWorkbenchRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return ModContent.MISMATCHED_WORKBENCH_ID.toString();
        }
    };

    MismatchedWorkbenchResult assemblePair(MismatchedWorkbenchInput input);

    /** Loaded custom recipes retain their own assembly; rule-driven recipes can override this. */
    default MismatchedWorkbenchResult assemblePreview(
            MismatchedWorkbenchInput input, AttributeRuleSet attributes, ComponentRules components) {
        return assemblePair(input);
    }

    @Override
    default ItemStack assemble(MismatchedWorkbenchInput input) {
        MismatchedWorkbenchResult result = assemblePair(input);
        return result.isComplete() ? result.primary() : ItemStack.EMPTY;
    }

    @Override
    default boolean isSpecial() {
        return true;
    }

    @Override
    default boolean showNotification() {
        return false;
    }

    @Override
    default String group() {
        return "";
    }

    @Override
    default RecipeType<MismatchedWorkbenchRecipe> getType() {
        return TYPE;
    }

    @Override
    default PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
