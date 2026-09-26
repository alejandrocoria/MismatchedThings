package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CompositionRulesClientLifecycleTest {
    @AfterEach
    void restoreDefaults() {
        AttributeRuleState.restoreDefaults();
        ComponentRuleState.restoreDefaults();
    }

    @Test
    void connectClearsStaleRulesBeforeSendingExactlyOneReadyPayload() {
        AttributeRuleSet stale = rules(AttributePolicy.COMBINE);
        ComponentRules staleComponents = componentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER);
        AttributeRuleState.install(stale);
        ComponentRuleState.install(staleComponents);
        AtomicInteger sends = new AtomicInteger();

        CompositionRulesClientLifecycle.onConnected(() -> {
            assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
            assertSame(ComponentRuleDefaults.state(), ComponentRuleState.current());
            sends.incrementAndGet();
        });

        assertEquals(1, sends.get());
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
        assertSame(ComponentRuleDefaults.state(), ComponentRuleState.current());
    }

    @Test
    void snapshotReplacementIsAtomicAndDisconnectRestoresDefaults() {
        AttributeRuleSet synchronizedRules = rules(AttributePolicy.MAXIMUM);
        ComponentRules synchronizedComponents = componentRules(
                DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER);

        AttributeRuleState.install(synchronizedRules);
        ComponentRuleState.install(synchronizedComponents);

        assertSame(synchronizedRules, AttributeRuleState.current());
        assertSame(synchronizedComponents, ComponentRuleState.current());
        CompositionRulesClientLifecycle.onDisconnected();
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
        assertSame(ComponentRuleDefaults.state(), ComponentRuleState.current());
    }

    private static AttributeRuleSet rules(AttributePolicy policy) {
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        rules.put(Identifier.withDefaultNamespace("armor"), policy);
        return new AttributeRuleSet(AttributePolicy.SEPARATE, rules);
    }

    private static ComponentRules componentRules(
            DurabilityPolicy durability, RepairCostPolicy repairCost) {
        return new ComponentRules(durability, repairCost, ComponentRuleDefaults.ruleSet());
    }
}
