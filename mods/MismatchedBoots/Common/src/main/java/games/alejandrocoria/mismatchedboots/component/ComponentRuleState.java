package games.alejandrocoria.mismatchedboots.component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Client preview rules, populated by synchronization; never server authority. */
public final class ComponentRuleState {
    private static final AtomicReference<ComponentRules> CURRENT =
            new AtomicReference<>(ComponentRuleDefaults.state());

    private ComponentRuleState() {
    }

    public static ComponentRules current() {
        return CURRENT.get();
    }

    public static void install(ComponentRules rules) {
        CURRENT.set(Objects.requireNonNull(rules, "rules"));
    }

    public static void restoreDefaults() {
        CURRENT.set(ComponentRuleDefaults.state());
    }
}
