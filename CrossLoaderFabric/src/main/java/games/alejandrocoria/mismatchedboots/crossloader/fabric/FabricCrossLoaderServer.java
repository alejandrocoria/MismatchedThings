package games.alejandrocoria.mismatchedboots.crossloader.fabric;

import games.alejandrocoria.mismatchedboots.crossloader.CrossLoaderServerTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricCrossLoaderServer implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(CrossLoaderServerTest::tick);
    }
}
