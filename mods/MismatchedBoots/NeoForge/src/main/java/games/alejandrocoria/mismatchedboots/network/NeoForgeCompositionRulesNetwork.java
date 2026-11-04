package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgeCompositionRulesNetwork {
    private NeoForgeCompositionRulesNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(CompositionRulesProtocol.REVISION);
        registrar.playToServer(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC,
                (payload, context) -> PacketDistributor.sendToPlayer((ServerPlayer) context.player(),
                        new CompositionRulesPayload(new CompositionRules(
                                AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules()))));
        registrar.playToClient(CompositionRulesPayload.TYPE, CompositionRulesPayload.STREAM_CODEC,
                (payload, context) -> CompositionRulesClientLifecycle.install(payload.rules()));
    }
}
