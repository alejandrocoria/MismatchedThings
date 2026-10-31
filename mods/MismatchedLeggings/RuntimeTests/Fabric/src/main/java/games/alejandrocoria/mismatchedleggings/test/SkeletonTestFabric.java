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
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.parse("leggings_skeleton_test:charge"), CompositionFixture.CHARGE);
        var loader = FabricLoader.getInstance();
        SkeletonChecks.environment(loader.isModLoaded("mismatchedboots"),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedleggings")).count(),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedthings")).count());
        ServerTickEvents.END_SERVER_TICK.register(SkeletonChecks::serverTick);
    }
}
