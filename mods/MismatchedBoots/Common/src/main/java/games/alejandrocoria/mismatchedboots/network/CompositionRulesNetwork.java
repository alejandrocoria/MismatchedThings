package games.alejandrocoria.mismatchedboots.network;

import commonnetwork.api.Network;
import commonnetwork.networking.data.PacketContext;
import commonnetwork.networking.data.Side;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CompositionRulesNetwork {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private CompositionRulesNetwork() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        Network.registerPacket(ClientReadyPayload.TYPE, ClientReadyPayload.class, ClientReadyPayload.STREAM_CODEC,
                               CompositionRulesNetwork::handleClientReady);
        Network.registerPacket(AttributeRulesPayload.TYPE, AttributeRulesPayload.class, AttributeRulesPayload.STREAM_CODEC,
                               CompositionRulesNetwork::handleAttributeRules);
        Network.registerPacket(ComponentRulesPayload.TYPE, ComponentRulesPayload.class, ComponentRulesPayload.STREAM_CODEC,
                               CompositionRulesNetwork::handleComponentRules);
    }

    private static void handleClientReady(PacketContext<ClientReadyPayload> context) {
        if (context.side() != Side.SERVER) {
            Constants.LOG.warn("Ignoring client-ready payload received on {}", context.side());
            return;
        }

        ServerPlayer sender = context.sender();
        if (sender == null) {
            Constants.LOG.warn("Ignoring client-ready payload without a sender");
            return;
        }

        Network.getNetworkHandler().sendToClient(
                new AttributeRulesPayload(AttributeRulesLifecycle.serverRules()), sender, true);
        Network.getNetworkHandler().sendToClient(
                new ComponentRulesPayload(ComponentRulesLifecycle.serverRules()), sender, true);
    }

    private static void handleAttributeRules(PacketContext<AttributeRulesPayload> context) {
        if (context.side() != Side.CLIENT) {
            Constants.LOG.warn("Ignoring attribute-rules payload received on {}", context.side());
            return;
        }
        CompositionRulesClientLifecycle.installAttributeRules(context.message().rules());
    }

    private static void handleComponentRules(PacketContext<ComponentRulesPayload> context) {
        if (context.side() != Side.CLIENT) {
            Constants.LOG.warn("Ignoring component-rules payload received on {}", context.side());
            return;
        }
        CompositionRulesClientLifecycle.installComponentRules(context.message().rules());
    }
}
