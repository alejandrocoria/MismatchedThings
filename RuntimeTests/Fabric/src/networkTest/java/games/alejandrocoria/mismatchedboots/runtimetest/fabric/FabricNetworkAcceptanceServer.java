package games.alejandrocoria.mismatchedboots.runtimetest.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceServerTest;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorServerProbe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricNetworkAcceptanceServer implements ModInitializer {
    @Override
    public void onInitialize() {
        if (SeparatorObservations.enabled()) {
            if (Boolean.getBoolean("mismatchedboots.test.separatorSeed")) {
                ServerTickEvents.END_SERVER_TICK.register(SeparatorServerProbe::run);
            }
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(NetworkAcceptanceServerTest::tick);
    }
}
