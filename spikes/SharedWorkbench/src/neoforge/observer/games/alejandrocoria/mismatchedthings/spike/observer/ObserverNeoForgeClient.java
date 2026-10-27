package games.alejandrocoria.mismatchedthings.spike.observer;



import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ObserverNeoForgeClient {
    private ObserverNeoForgeClient() {
    }

    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> SpikeClient.tick(Minecraft.getInstance()));
    }
}
