package games.alejandrocoria.mismatchedthings.fixture.baseline;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class BaselineFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BaselineRecipe.install();
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, BaselineRecipe.ID, BaselineRecipe.SERIALIZER);
    }
}
