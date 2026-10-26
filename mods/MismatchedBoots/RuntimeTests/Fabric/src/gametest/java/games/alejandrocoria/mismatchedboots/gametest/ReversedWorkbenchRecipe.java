package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A test-only datapack replacement whose actual output differs from the built-in recipe. */
public final class ReversedWorkbenchRecipe implements MismatchedWorkbenchRecipe {
    private static final ReversedWorkbenchRecipe INSTANCE = new ReversedWorkbenchRecipe();
    public static final RecipeSerializer<ReversedWorkbenchRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    @Override
    public boolean matches(MismatchedWorkbenchInput input, Level level) {
        return new MixedBootsWorkbenchRecipe().matches(input, level);
    }

    @Override
    public MismatchedWorkbenchResult assemblePair(MismatchedWorkbenchInput input) {
        var pair = MixedBootsFactory.createPair(input.right(), input.left());
        return new MismatchedWorkbenchResult(pair.primary(), pair.inverse());
    }

    @Override
    public MismatchedWorkbenchResult assemblePreview(
            MismatchedWorkbenchInput input, AttributeRuleSet attributes, ComponentRules components) {
        var pair = MixedBootsFactory.createPair(input.right(), input.left(), attributes, components);
        return new MismatchedWorkbenchResult(pair.primary(), pair.inverse());
    }

    @Override
    public RecipeSerializer<ReversedWorkbenchRecipe> getSerializer() {
        return SERIALIZER;
    }
}
