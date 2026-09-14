package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.platform.Services;

import java.nio.file.Path;

public final class AttributeRulesLifecycle {
    private AttributeRulesLifecycle() {
    }

    public static void onServerStarting() {
        load(Services.PLATFORM.getConfigDirectory().resolve(AttributeRulesConfig.FILE_NAME));
    }

    public static void onServerStopped() {
        AttributeRuleState.restoreDefaults();
    }

    static AttributeRuleSet load(Path path) {
        AttributeRuleSet rules = new AttributeRulesConfig(path).load();
        AttributeRuleState.install(rules);
        Constants.LOG.info("Loaded {} attribute composition rules from {}", rules.rules().size(), path);
        return rules;
    }
}
