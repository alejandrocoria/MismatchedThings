package games.alejandrocoria.mismatchedboots.network;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;

public final class CompositionRulesClientLifecycle {
    private CompositionRulesClientLifecycle() {
    }

    public static void onConnected() {
        onConnected(() -> Network.getNetworkHandler().sendToServer(ClientReadyPayload.INSTANCE, true));
    }

    public static void onDisconnected() {
        AttributeRuleState.restoreDefaults();
        ComponentRuleState.restoreDefaults();
    }

    static void onConnected(Runnable readySender) {
        AttributeRuleState.restoreDefaults();
        ComponentRuleState.restoreDefaults();
        readySender.run();
    }
}
