package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricProtocolObserver implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(ProtocolEvidence::started);
        ServerTickEvents.END_SERVER_TICK.register(ProtocolEvidence::tick);
    }
}
