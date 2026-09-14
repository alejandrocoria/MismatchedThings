package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.network.AttributeRulesClientLifecycle;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class MismatchedBootsClientForge {
    public static void onClientSetup(FMLClientSetupEvent event) {
        MismatchedBootsClient.init();
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(
                login -> AttributeRulesClientLifecycle.onConnected());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
                logout -> AttributeRulesClientLifecycle.onDisconnected());
    }
}
