package games.alejandrocoria.mismatchedboots.client;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class MismatchedBootsClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MismatchedBootsClient.init();
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> CompositionRulesClientLifecycle.onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> CompositionRulesClientLifecycle.onDisconnected());
    }
}
