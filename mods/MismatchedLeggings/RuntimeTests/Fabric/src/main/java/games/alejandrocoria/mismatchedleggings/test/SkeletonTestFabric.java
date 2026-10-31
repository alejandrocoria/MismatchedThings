package games.alejandrocoria.mismatchedleggings.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SkeletonTestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        var loader = FabricLoader.getInstance();
        SkeletonChecks.environment(loader.isModLoaded("mismatchedboots"),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedleggings")).count(),
                loader.getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals("mismatchedthings")).count());
        ServerTickEvents.END_SERVER_TICK.register(SkeletonChecks::serverTick);
    }
}
