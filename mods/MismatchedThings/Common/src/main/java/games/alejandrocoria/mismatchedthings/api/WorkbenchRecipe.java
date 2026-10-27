package games.alejandrocoria.mismatchedthings.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public interface WorkbenchRecipe extends Recipe<WorkbenchInput> {
    RecipeType<WorkbenchRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return MismatchedWorkbench.ID.toString();
        }
    };

    WorkbenchResult assemblePair(WorkbenchInput input);

    /** Client preview only: consumers resolve their own synchronized policies without exposing them to the core. */
    default WorkbenchResult assemblePreview(
            WorkbenchInput input, Level level) {
        return assemblePair(input);
    }

    @Override
    default ItemStack assemble(WorkbenchInput input) {
        WorkbenchResult result = assemblePair(input);
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
    default RecipeType<WorkbenchRecipe> getType() {
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
