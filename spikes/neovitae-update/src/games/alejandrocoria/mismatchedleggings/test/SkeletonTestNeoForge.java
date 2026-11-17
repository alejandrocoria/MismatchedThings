package games.alejandrocoria.mismatchedleggings.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in replacement for the ordinary fixture entry point, not a production entry point. */
@Mod("leggings_skeleton_test")
public final class SkeletonTestNeoForge {
    public SkeletonTestNeoForge(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NeoVitaeUpdateSpike.tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(NeoVitaeUpdateClient::setup);
        }
    }
}
