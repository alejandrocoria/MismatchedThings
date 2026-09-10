package games.alejandrocoria.mismatchedboots.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class MismatchedBootsClientNeoForge {
    public MismatchedBootsClientNeoForge() {
    }

    public static void onClientSetup(FMLClientSetupEvent event, IEventBus eventBus) {
        MismatchedBootsClient.init();
    }
}
