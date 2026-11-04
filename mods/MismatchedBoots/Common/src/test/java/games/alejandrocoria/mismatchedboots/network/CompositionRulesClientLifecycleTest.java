package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionRulesClientLifecycleTest {
    @AfterEach
    void disconnect() {
        CompositionRulesClientLifecycle.onDisconnected();
    }

    @Test
    void connectInvalidatesAndNotifiesBeforeSendingExactlyOneReadyPayload() {
        CompositionRulesClientLifecycle.install(rules(AttributePolicy.COMBINE));
        AtomicInteger notifications = new AtomicInteger();
        Runnable remove = CompositionRulesClientLifecycle.addRulesListener(notifications::incrementAndGet);
        AtomicInteger sends = new AtomicInteger();

        try {
            CompositionRulesClientLifecycle.onConnected(() -> {
                assertNull(CompositionRulesClientLifecycle.synchronizedRules());
                assertFalse(CompositionRulesClientLifecycle.areRulesSynchronized());
                assertEquals(2, notifications.get(), "Invalidation must reach consumers before the ready send");
                sends.incrementAndGet();
            });

            assertEquals(1, sends.get());
        } finally {
            remove.run();
        }
    }

    @Test
    void installPublishesBothSectionsAndDisconnectLeavesNoFallbackSnapshot() {
        CompositionRules snapshot = rules(AttributePolicy.MAXIMUM);
        CompositionRulesClientLifecycle.install(snapshot);

        assertSame(snapshot, CompositionRulesClientLifecycle.synchronizedRules());
        assertTrue(CompositionRulesClientLifecycle.areRulesSynchronized());

        CompositionRulesClientLifecycle.onDisconnected();
        assertNull(CompositionRulesClientLifecycle.synchronizedRules());
        assertFalse(CompositionRulesClientLifecycle.areRulesSynchronized());
    }

    @Test
    void listenersObserveCompleteReplacementAndInvalidationAndCanBeRemoved() {
        AtomicInteger notifications = new AtomicInteger();
        Runnable remove = CompositionRulesClientLifecycle.addRulesListener(notifications::incrementAndGet);
        try {
            assertEquals(0, notifications.get());
            CompositionRules first = rules(AttributePolicy.MAXIMUM);
            CompositionRulesClientLifecycle.install(first);
            assertEquals(1, notifications.get());

            CompositionRules second = rules(AttributePolicy.COMBINE);
            CompositionRulesClientLifecycle.install(second);
            assertSame(second, CompositionRulesClientLifecycle.synchronizedRules());
            assertEquals(2, notifications.get());

            CompositionRulesClientLifecycle.onDisconnected();
            assertEquals(3, notifications.get());
        } finally {
            remove.run();
        }

        CompositionRulesClientLifecycle.install(rules(AttributePolicy.SEPARATE));
        assertEquals(3, notifications.get());
    }

    @Test
    void lateListenerRunsImmediatelyAndReconnectWaitsForTheNewServerSnapshot() {
        CompositionRulesClientLifecycle.install(rules(AttributePolicy.COMBINE));
        AtomicInteger notifications = new AtomicInteger();
        Runnable remove = CompositionRulesClientLifecycle.addRulesListener(notifications::incrementAndGet);
        try {
            assertEquals(1, notifications.get());
            CompositionRulesClientLifecycle.onConnected(() -> {});
            assertEquals(2, notifications.get());
            assertNull(CompositionRulesClientLifecycle.synchronizedRules());

            CompositionRules nextServer = rules(AttributePolicy.MAXIMUM);
            CompositionRulesClientLifecycle.install(nextServer);
            assertSame(nextServer, CompositionRulesClientLifecycle.synchronizedRules());
            assertEquals(3, notifications.get());
        } finally {
            remove.run();
        }
    }

    @Test
    void failingListenerDoesNotPreventOtherConsumersFromReceivingChanges() {
        Runnable removeFailing = CompositionRulesClientLifecycle.addRulesListener(() -> {
            throw new IllegalStateException("test listener failure");
        });
        AtomicInteger notifications = new AtomicInteger();
        Runnable removeHealthy = CompositionRulesClientLifecycle.addRulesListener(notifications::incrementAndGet);
        try {
            CompositionRulesClientLifecycle.install(rules(AttributePolicy.COMBINE));
            CompositionRulesClientLifecycle.onDisconnected();
            assertEquals(2, notifications.get());
        } finally {
            removeFailing.run();
            removeHealthy.run();
        }
    }

    private static CompositionRules rules(AttributePolicy policy) {
        return new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE,
                Map.of(Identifier.withDefaultNamespace("armor"), policy)), ComponentRuleDefaults.state());
    }
}
