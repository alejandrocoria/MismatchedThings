package games.alejandrocoria.mismatchedleggings.config;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;

import java.nio.file.Path;
import java.util.Objects;

/** One immutable snapshot; lifecycle and synchronization are wired in block D. */
public record CompositionRules(AttributeRuleSet attributes, ComponentRules components) {
    public CompositionRules {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(components, "components");
    }

    public static CompositionRules defaults() {
        return new CompositionRules(AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
    }

    public static CompositionRules load(Path directory) {
        return new CompositionRules(new AttributeRulesConfig(directory.resolve(AttributeRulesConfig.FILE_NAME)).load(),
                new ComponentRulesConfig(directory.resolve(ComponentRulesConfig.FILE_NAME)).load());
    }
}
