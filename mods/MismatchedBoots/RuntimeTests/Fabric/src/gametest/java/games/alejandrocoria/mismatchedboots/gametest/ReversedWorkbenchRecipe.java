package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A test-only datapack replacement whose actual output differs from the built-in recipe. */
public final class ReversedWorkbenchRecipe implements WorkbenchRecipe {
    private static final ReversedWorkbenchRecipe INSTANCE = new ReversedWorkbenchRecipe();
    public static final RecipeSerializer<ReversedWorkbenchRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return new MixedBootsWorkbenchRecipe().matches(input, level);
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        var pair = MixedBootsFactory.createPair(input.right(), input.left());
        return new WorkbenchResult(pair.primary(), pair.inverse());
    }

    @Override
    public WorkbenchResult assemblePreview(
            WorkbenchInput input, Level level) {
        var rules = CompositionRulesClientLifecycle.synchronizedRules();
        if (rules == null) {
            return WorkbenchResult.EMPTY;
        }
        var pair = MixedBootsFactory.createPair(input.right(), input.left(), rules.attributes(), rules.components());
        return new WorkbenchResult(pair.primary(), pair.inverse());
    }

    @Override
    public RecipeSerializer<ReversedWorkbenchRecipe> getSerializer() {
        return SERIALIZER;
    }
}
