package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.network.ClientReadyPayload;
import games.alejandrocoria.mismatchedleggings.network.CompositionRulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MismatchedLeggingsClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CompositionRulesPayload.TYPE,
                (payload, context) -> CompositionRuleState.installPreview(payload.rules()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CompositionRuleState.resetPreview();
            sender.sendPacket(new ClientReadyPayload());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CompositionRuleState.resetPreview());
    }
}
