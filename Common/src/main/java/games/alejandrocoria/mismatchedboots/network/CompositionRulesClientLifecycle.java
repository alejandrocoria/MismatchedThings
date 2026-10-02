package games.alejandrocoria.mismatchedboots.network;

import commonnetwork.api.Network;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompositionRulesClientLifecycle {
    private static final Object SYNCHRONIZATION_LOCK = new Object();
    private static final List<Runnable> SYNCHRONIZED_LISTENERS = new ArrayList<>();

    private static boolean attributeRulesSynchronized;
    private static boolean componentRulesSynchronized;
    private static boolean synchronizationNotified;

    private CompositionRulesClientLifecycle() {
    }

    public static void onConnected() {
        onConnected(() -> Network.getNetworkHandler().sendToServer(ClientReadyPayload.INSTANCE, true));
    }

    public static void onDisconnected() {
        AttributeRuleState.restoreDefaults();
        ComponentRuleState.restoreDefaults();
        resetSynchronization();
    }

    static void installAttributeRules(AttributeRuleSet rules) {
        AttributeRuleState.install(rules);
        markSynchronized(true);
    }

    static void installComponentRules(ComponentRules rules) {
        ComponentRuleState.install(rules);
        markSynchronized(false);
    }

    public static boolean areRulesSynchronized() {
        synchronized (SYNCHRONIZATION_LOCK) {
            return attributeRulesSynchronized && componentRulesSynchronized;
        }
    }

    public static Runnable addSynchronizedListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        boolean notifyImmediately;
        synchronized (SYNCHRONIZATION_LOCK) {
            SYNCHRONIZED_LISTENERS.add(listener);
            notifyImmediately = attributeRulesSynchronized && componentRulesSynchronized;
        }
        if (notifyImmediately) {
            runListener(listener);
        }
        return () -> {
            synchronized (SYNCHRONIZATION_LOCK) {
                SYNCHRONIZED_LISTENERS.remove(listener);
            }
        };
    }

    static void onConnected(Runnable readySender) {
        AttributeRuleState.restoreDefaults();
        ComponentRuleState.restoreDefaults();
        resetSynchronization();
        readySender.run();
    }

    private static void markSynchronized(boolean attributes) {
        List<Runnable> listeners = List.of();
        synchronized (SYNCHRONIZATION_LOCK) {
            if (attributes) {
                attributeRulesSynchronized = true;
            } else {
                componentRulesSynchronized = true;
            }
            if (attributeRulesSynchronized && componentRulesSynchronized && !synchronizationNotified) {
                synchronizationNotified = true;
                listeners = List.copyOf(SYNCHRONIZED_LISTENERS);
            }
        }
        listeners.forEach(CompositionRulesClientLifecycle::runListener);
    }

    private static void resetSynchronization() {
        synchronized (SYNCHRONIZATION_LOCK) {
            attributeRulesSynchronized = false;
            componentRulesSynchronized = false;
            synchronizationNotified = false;
        }
    }

    private static void runListener(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException exception) {
            Constants.LOG.error("Composition-rules synchronization listener failed", exception);
        }
    }
}
