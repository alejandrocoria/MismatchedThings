package games.alejandrocoria.rulesspike;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

public final class FabricClientSpike implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!Proof.CANDIDATE) {
            ClientTickEvents.END_CLIENT_TICK.register(RejectionProof::tick);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(ClientProof::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientProof.disconnected());
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(Ready.TYPE,
                (payload, context) -> { });
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.COMPLETE.register((listener, client) -> {
            if (!net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.canSend(Ready.TYPE)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.getSender()
                        .disconnect(Component.literal(Proof.REASON));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (payload, context) -> PreviewState.install(payload));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            ClientProof.joining();
            if (!ClientPlayNetworking.canSend(Ready.TYPE)) {
                listener.getConnection().disconnect(Component.literal(Proof.REASON));
                return;
            }
            ClientPlayNetworking.send(Ready.INSTANCE);
        });
    }
}
