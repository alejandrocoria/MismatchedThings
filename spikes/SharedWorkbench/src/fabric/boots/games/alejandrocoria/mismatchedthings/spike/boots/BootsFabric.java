package games.alejandrocoria.mismatchedthings.spike.boots;



import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class BootsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BootsRecipe.install();
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, BootsRecipe.ID, BootsRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(BootsRecipe.SERIALIZER);
    }
}
