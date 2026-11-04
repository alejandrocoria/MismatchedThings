package games.alejandrocoria.mismatchedboots.runtimetest.protocol;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod("mismatchedboots_protocol_test")
public final class NeoForgeProtocolObserver {
    public NeoForgeProtocolObserver(IEventBus eventBus) {
        if (Boolean.getBoolean("mismatchedboots.runtimetest.protocolNativeControl")) {
            if (ModList.get().isLoaded("mismatchedboots") || ModList.get().isLoaded("mismatchedthings")) {
                throw new IllegalStateException("Native control must not load Boots or Things");
            }
            ProtocolEvidence.write("native-control-no-products.txt", "Boots and Things are absent");
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.protocolControlAdvertise")) {
            eventBus.addListener((RegisterPayloadHandlersEvent event) ->
                    event.registrar("2").playToClient(NativeControlPayload.TYPE, NativeControlPayload.CODEC,
                            (payload, context) -> {}));
        }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ProtocolEvidence.started(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ProtocolEvidence.tick(event.getServer()));
    }
}
