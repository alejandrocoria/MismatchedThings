package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class WorkbenchTestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        WorkbenchChecks.require(!FabricLoader.getInstance().isModLoaded("mismatchedboots"), "Core tests must not load Boots");
        if (WorkbenchChecks.RECIPES) {
            MismatchedWorkbench.registerContributor(FixtureRecipe.ID, FixtureRecipe::accepts);
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, FixtureRecipe.ID, FixtureRecipe.SERIALIZER);
        }
        ServerTickEvents.END_SERVER_TICK.register(WorkbenchChecks::serverTick);
    }
}
