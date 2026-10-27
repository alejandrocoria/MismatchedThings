package games.alejandrocoria.mismatchedthings.spike.addon;



import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class AddonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AddonRecipe.install();
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, AddonRecipe.ID, AddonRecipe.SERIALIZER);
        RecipeSynchronization.synchronizeRecipeSerializer(AddonRecipe.SERIALIZER);
    }
}
