package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.platform.Services;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class AttributeRulesLifecycle {
    private static final AtomicReference<AttributeRuleSet> SERVER_RULES =
            new AtomicReference<>(AttributeRuleDefaults.ruleSet());

    private AttributeRulesLifecycle() {
    }

    public static void onServerStarting() {
        load(Services.PLATFORM.getConfigDirectory().resolve(AttributeRulesConfig.FILE_NAME));
    }

    public static void onServerStopped() {
        SERVER_RULES.set(AttributeRuleDefaults.ruleSet());
    }

    public static AttributeRuleSet serverRules() {
        return SERVER_RULES.get();
    }

    static AttributeRuleSet load(Path path) {
        AttributeRuleSet rules = new AttributeRulesConfig(path).load();
        SERVER_RULES.set(rules);
        Constants.LOG.info("Loaded {} attribute composition rules from {}", rules.rules().size(), path);
        return rules;
    }
}
