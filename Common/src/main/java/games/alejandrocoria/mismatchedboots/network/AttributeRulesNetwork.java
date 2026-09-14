package games.alejandrocoria.mismatchedboots.network;

import commonnetwork.api.Network;
import commonnetwork.networking.data.PacketContext;
import commonnetwork.networking.data.Side;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AttributeRulesNetwork {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private AttributeRulesNetwork() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        Network.registerPacket(ClientReadyPayload.TYPE, ClientReadyPayload.STREAM_CODEC,
                AttributeRulesNetwork::handleClientReady);
        Network.registerPacket(AttributeRulesPayload.TYPE, AttributeRulesPayload.STREAM_CODEC,
                AttributeRulesNetwork::handleAttributeRules);
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
    }

    private static void handleAttributeRules(PacketContext<AttributeRulesPayload> context) {
        if (context.side() != Side.CLIENT) {
            Constants.LOG.warn("Ignoring attribute-rules payload received on {}", context.side());
            return;
        }
        AttributeRuleState.install(context.message().rules());
    }
}
