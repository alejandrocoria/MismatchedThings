package games.alejandrocoria.mismatchedthings.spike.core;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeType;

public interface WorkbenchRecipe extends Recipe<WorkbenchInput> {
    RecipeType<WorkbenchRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return WorkbenchCore.MISMATCHED_WORKBENCH_ID.toString();
        }
    };

    WorkbenchResult assemblePair(WorkbenchInput input);

    /** Loaded custom recipes retain their own assembly; rule-driven recipes can override this. */
    default WorkbenchResult assemblePreview(
            WorkbenchInput input, net.minecraft.world.level.Level level) {
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
