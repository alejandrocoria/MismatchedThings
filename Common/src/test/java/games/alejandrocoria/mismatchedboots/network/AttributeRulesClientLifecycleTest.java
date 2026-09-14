package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttributeRulesClientLifecycleTest {
    @AfterEach
    void restoreDefaults() {
        AttributeRuleState.restoreDefaults();
    }

    @Test
    void connectClearsStaleRulesBeforeSendingExactlyOneReadyPayload() {
        AttributeRuleSet stale = rules(AttributePolicy.COMBINE);
        AttributeRuleState.install(stale);
        AtomicInteger sends = new AtomicInteger();

        AttributeRulesClientLifecycle.onConnected(() -> {
            assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
            sends.incrementAndGet();
        });

        assertEquals(1, sends.get());
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
    }

    @Test
    void snapshotReplacementIsAtomicAndDisconnectRestoresDefaults() {
        AttributeRuleSet synchronizedRules = rules(AttributePolicy.MAXIMUM);

        AttributeRuleState.install(synchronizedRules);

        assertSame(synchronizedRules, AttributeRuleState.current());
        AttributeRulesClientLifecycle.onDisconnected();
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
    }

    private static AttributeRuleSet rules(AttributePolicy policy) {
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        rules.put(Identifier.withDefaultNamespace("armor"), policy);
        return new AttributeRuleSet(AttributePolicy.SEPARATE, rules);
    }
}
