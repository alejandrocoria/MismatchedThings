package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** A loaded replacement changes preview results without touching production's recipe cache. */
public final class ViewerRecipe implements WorkbenchRecipe {
    private static final ViewerRecipe INSTANCE = new ViewerRecipe();
    public static final RecipeSerializer<ViewerRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));
    private final MixedLeggingsWorkbenchRecipe delegate = new MixedLeggingsWorkbenchRecipe();

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        var pair = delegate.assemblePair(input);
        return pair.isComplete() ? new WorkbenchResult(pair.inverse(), pair.primary()) : WorkbenchResult.EMPTY;
    }

    @Override
    public WorkbenchResult assemblePreview(WorkbenchInput input, Level level) {
        var pair = delegate.assemblePreview(input, level);
        return pair.isComplete() ? new WorkbenchResult(pair.inverse(), pair.primary()) : WorkbenchResult.EMPTY;
    }

    @Override
    public RecipeSerializer<ViewerRecipe> getSerializer() {
        return SERIALIZER;
    }
}
