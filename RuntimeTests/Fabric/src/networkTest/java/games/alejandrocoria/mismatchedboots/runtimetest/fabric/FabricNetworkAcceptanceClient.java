package games.alejandrocoria.mismatchedboots.runtimetest.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceClientTest;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorClientTest;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricNetworkAcceptanceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsProof.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsClient::tick);
            return;
        }
        if (games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycleClient::tick);
            return;
        }
        if (SeparatorObservations.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(SeparatorClientTest::tick);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(NetworkAcceptanceClientTest::tick);
    }
}
