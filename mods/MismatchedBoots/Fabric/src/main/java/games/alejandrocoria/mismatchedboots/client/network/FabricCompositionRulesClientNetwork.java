package games.alejandrocoria.mismatchedboots.client.network;

import games.alejandrocoria.mismatchedboots.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedboots.network.FabricCompositionRulesNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FabricCompositionRulesClientNetwork {
    private FabricCompositionRulesClientNetwork() {
    }

    public static void initialize() {
        ClientConfigurationNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE, (payload, context) -> {
        });
        ClientConfigurationConnectionEvents.COMPLETE.register((listener, client) -> {
            if (!ClientConfigurationNetworking.canSend(ClientReadyPayload.TYPE)) {
                ClientConfigurationNetworking.getSender().disconnect(FabricCompositionRulesNetwork.mismatchReason());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CompositionRulesPayload.TYPE,
                (payload, context) -> CompositionRulesClientLifecycle.install(payload.rules()));
    }

    public static void onConnected() {
        CompositionRulesClientLifecycle.onConnected(() -> {
            if (!ClientPlayNetworking.canSend(ClientReadyPayload.TYPE)) {
                ClientPlayNetworking.getSender().disconnect(FabricCompositionRulesNetwork.mismatchReason());
                return;
            }
            sendReady();
        });
    }

    public static void sendReady() {
        ClientPlayNetworking.send(ClientReadyPayload.INSTANCE);
    }
}
