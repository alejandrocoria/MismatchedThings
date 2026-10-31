package games.alejandrocoria.leggingsspike;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("leggings_spike")
public final class NeoForgeSpike {
    public NeoForgeSpike(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> {
            SpikeClient.withBoots = ModList.get().isLoaded("mismatchedboots");
            NativeModels.prepareGallery();
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> SpikeClient.tick(Minecraft.getInstance()));
        });
        NativeModels.register(bus);
    }
}
