package games.alejandrocoria.mismatchedboots.network;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;

public final class AttributeRulesClientLifecycle {
    private AttributeRulesClientLifecycle() {
    }

    public static void onConnected() {
        onConnected(() -> Network.getNetworkHandler().sendToServer(ClientReadyPayload.INSTANCE, true));
    }

    public static void onDisconnected() {
        AttributeRuleState.restoreDefaults();
    }

    static void onConnected(Runnable readySender) {
        AttributeRuleState.restoreDefaults();
        readySender.run();
    }
}
