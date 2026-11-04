package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;

import java.util.Objects;

/** Complete immutable rules for one client preview; server configuration owners remain separate. */
public record CompositionRules(AttributeRuleSet attributes, ComponentRules components) {
    public CompositionRules {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(components, "components");
    }
}
