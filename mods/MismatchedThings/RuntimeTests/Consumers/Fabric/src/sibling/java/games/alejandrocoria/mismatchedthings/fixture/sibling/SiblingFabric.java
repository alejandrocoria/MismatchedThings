package games.alejandrocoria.mismatchedthings.fixture.sibling;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SiblingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SiblingRecipe.install();
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, SiblingRecipe.ID, SiblingRecipe.SERIALIZER);
    }
}
