package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.platform.Services;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class ComponentRulesLifecycle {
    private static final AtomicReference<ComponentRules> SERVER_RULES =
            new AtomicReference<>(ComponentRuleDefaults.state());

    private ComponentRulesLifecycle() {
    }

    public static void onServerStarting() {
        load(Services.PLATFORM.getConfigDirectory().resolve(ComponentRulesConfig.FILE_NAME));
    }

    public static void onServerStopped() {
        SERVER_RULES.set(ComponentRuleDefaults.state());
        ComponentRuleState.restoreDefaults();
    }

    public static ComponentRules serverRules() {
        return SERVER_RULES.get();
    }

    static ComponentRules load(Path path) {
        ComponentRules rules = new ComponentRulesConfig(path).load();
        SERVER_RULES.set(rules);
        ComponentRuleState.install(rules);
        Constants.LOG.info("Loaded {} component composition declarations from {}", rules.declarations().size(), path);
        return rules;
    }
}
