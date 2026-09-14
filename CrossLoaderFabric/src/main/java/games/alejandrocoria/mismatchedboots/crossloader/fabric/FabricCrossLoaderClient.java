package games.alejandrocoria.mismatchedboots.crossloader.fabric;

import games.alejandrocoria.mismatchedboots.crossloader.CrossLoaderClientTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricCrossLoaderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CrossLoaderClientTest::tick);
    }
}
