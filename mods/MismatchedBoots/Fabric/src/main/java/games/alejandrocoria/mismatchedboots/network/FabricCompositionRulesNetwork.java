package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class FabricCompositionRulesNetwork {
    private FabricCompositionRulesNetwork() {
    }

    public static Component mismatchReason() {
        return Component.translatableWithFallback("disconnect.mismatchedboots.protocol_mismatch",
                "Mismatched Boots requires compatible network protocols on the client and server. Use matching Boots builds.");
    }

    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompositionRulesPayload.TYPE, CompositionRulesPayload.STREAM_CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE, (payload, context) -> {
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, ClientReadyPayload.TYPE)) {
                listener.disconnect(mismatchReason());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ClientReadyPayload.TYPE,
                (payload, context) -> sendRules(context.player()));
    }

    public static void sendRules(ServerPlayer player) {
        ServerPlayNetworking.send(player, new CompositionRulesPayload(new CompositionRules(
                AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules())));
    }
}
