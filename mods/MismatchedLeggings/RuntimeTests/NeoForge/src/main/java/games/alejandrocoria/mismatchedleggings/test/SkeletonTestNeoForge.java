package games.alejandrocoria.mismatchedleggings.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("leggings_skeleton_test")
public final class SkeletonTestNeoForge {
    public SkeletonTestNeoForge(IEventBus bus) {
        var mods = ModList.get();
        SkeletonChecks.environment(mods.isLoaded("mismatchedboots"),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedleggings")).count(),
                mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SkeletonChecks.serverTick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(SkeletonClientNeoForge::setup);
        }
    }
}
