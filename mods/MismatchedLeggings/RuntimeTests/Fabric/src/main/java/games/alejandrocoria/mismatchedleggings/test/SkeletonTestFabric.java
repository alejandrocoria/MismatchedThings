package games.alejandrocoria.mismatchedleggings.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class SkeletonTestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (Boolean.getBoolean("leggings.test.viewerBootsOnly")) {
            var loader = FabricLoader.getInstance();
            BootsOnlyViewerChecks.environment(loader.isModLoaded("mismatchedboots"), loader.isModLoaded("mismatchedleggings"),
                    loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedthings")).count());
            ServerTickEvents.END_SERVER_TICK.register(BootsOnlyViewerChecks::serverTick);
            return;
        }
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.parse("leggings_skeleton_test:charge"), CompositionFixture.CHARGE);
        if (Boolean.getBoolean("leggings.test.viewers")) {
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Identifier.parse("leggings_skeleton_test:reversed_workbench"), ViewerRecipe.SERIALIZER);
            net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization.synchronizeRecipeSerializer(ViewerRecipe.SERIALIZER);
        }
        if (RecoveryFixture.sourcesPresent()) {
            Registry.register(BuiltInRegistries.ITEM, RecoveryFixture.SOURCE, RecoveryFixture.source());
        }
        if (RecoveryFixture.walletPresent()) {
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RecoveryFixture.WALLET_ID, RecoveryFixture.WALLET);
        }
        var loader = FabricLoader.getInstance();
        SkeletonChecks.environment(loader.isModLoaded("mismatchedboots"),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedleggings")).count(),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedthings")).count());
        ServerTickEvents.END_SERVER_TICK.register(SkeletonChecks::serverTick);
    }
}
