package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class MismatchedBootsClientForge {
    public static void onClientSetup(FMLClientSetupEvent event) {
        MismatchedBootsClient.init();
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(
                login -> CompositionRulesClientLifecycle.onConnected());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
                logout -> CompositionRulesClientLifecycle.onDisconnected());
    }
}
