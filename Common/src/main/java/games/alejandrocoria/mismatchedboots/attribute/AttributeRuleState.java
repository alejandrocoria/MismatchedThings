package games.alejandrocoria.mismatchedboots.attribute;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AttributeRuleState {
    private static final AtomicReference<AttributeRuleSet> CURRENT =
            new AtomicReference<>(AttributeRuleDefaults.ruleSet());

    private AttributeRuleState() {
    }

    public static AttributeRuleSet current() {
        return CURRENT.get();
    }

    public static void install(AttributeRuleSet rules) {
        CURRENT.set(Objects.requireNonNull(rules, "rules"));
    }

    public static void restoreDefaults() {
        CURRENT.set(AttributeRuleDefaults.ruleSet());
    }
}
