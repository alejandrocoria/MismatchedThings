package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.network.AttributeRulesClientLifecycle;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public class MismatchedBootsClientNeoForge {
    public MismatchedBootsClientNeoForge() {
    }

    public static void onClientSetup(FMLClientSetupEvent event, IEventBus eventBus) {
        MismatchedBootsClient.init();
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn login) -> AttributeRulesClientLifecycle.onConnected());
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut logout) -> AttributeRulesClientLifecycle.onDisconnected());
    }
}
