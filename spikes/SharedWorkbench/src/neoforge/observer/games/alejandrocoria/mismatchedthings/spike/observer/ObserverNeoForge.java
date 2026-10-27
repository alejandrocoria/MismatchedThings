package games.alejandrocoria.mismatchedthings.spike.observer;



import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("shared_workbench_observer")
public final class ObserverNeoForge {
    public ObserverNeoForge(IEventBus bus) {
        var mods = ModList.get();
        SpikeChecks.version = mods.getModContainerById("mismatchedthings").orElseThrow().getModInfo().getVersion().toString();
        SpikeChecks.boots = mods.isLoaded("workbench_boots_consumer");
        SpikeChecks.addon = mods.isLoaded("workbench_addon_consumer");
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SpikeChecks.serverTick(event.getServer()));
        bus.addListener(ObserverNeoForgeClient::setup);
    }
}
