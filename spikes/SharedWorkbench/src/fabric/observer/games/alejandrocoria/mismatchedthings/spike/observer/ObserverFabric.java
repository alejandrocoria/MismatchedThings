package games.alejandrocoria.mismatchedthings.spike.observer;



import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class ObserverFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        var loader = FabricLoader.getInstance();
        SpikeChecks.version = loader.getModContainer("mismatchedthings").orElseThrow().getMetadata().getVersion().getFriendlyString();
        SpikeChecks.boots = loader.isModLoaded("workbench_boots_consumer");
        SpikeChecks.addon = loader.isModLoaded("workbench_addon_consumer");
        ServerTickEvents.END_SERVER_TICK.register(SpikeChecks::serverTick);
    }
}
