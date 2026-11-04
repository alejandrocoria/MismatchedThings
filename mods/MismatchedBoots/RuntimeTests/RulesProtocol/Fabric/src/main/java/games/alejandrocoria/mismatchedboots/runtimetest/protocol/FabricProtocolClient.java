package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class FabricProtocolClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> ProtocolRejectionClient.joined());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ProtocolRejectionClient.tick(client, false, null));
    }
}
