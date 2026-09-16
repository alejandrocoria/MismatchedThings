package games.alejandrocoria.mismatchedboots.crossloader.neoforge;

import games.alejandrocoria.mismatchedboots.crossloader.CrossLoaderClientTest;
import games.alejandrocoria.mismatchedboots.crossloader.CrossLoaderServerTest;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("mismatchedboots_crossloader_test")
public final class NeoForgeCrossLoaderTest {
    public NeoForgeCrossLoaderTest(IEventBus eventBus) {
        NeoForge.EVENT_BUS.addListener(
                (ServerTickEvent.Post event) -> CrossLoaderServerTest.tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            if (Boolean.getBoolean("mismatchedboots.crossloader.geometryTest")) {
                NeoForge.EVENT_BUS.addListener((ClientResourceLoadFinishedEvent event) -> {
                    if (event.isInitial()) {
                        NeoForgeGeometryClientTest.resourcesLoaded();
                    }
                });
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                        NeoForgeGeometryClientTest.tick(Minecraft.getInstance()));
            } else {
                NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
                        CrossLoaderClientTest.tick(Minecraft.getInstance()));
            }
        }
    }
}
