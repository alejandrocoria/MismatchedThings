package games.alejandrocoria.mismatchedboots.component;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ComponentRuleSet {
    private static final ComponentRule OWNER = ComponentRule.of(ComponentPolicy.OWNER);

    private final Map<Identifier, ComponentRule> rules;

    public ComponentRuleSet(Map<Identifier, ComponentRule> rules) {
        LinkedHashMap<Identifier, ComponentRule> copy = new LinkedHashMap<>();
        rules.forEach((id, rule) -> copy.put(
                Objects.requireNonNull(id, "component ID"),
                Objects.requireNonNull(rule, "component rule")));
        this.rules = Collections.unmodifiableMap(copy);
    }

    public Map<Identifier, ComponentRule> rules() {
        return rules;
    }

    public ComponentRule ruleFor(Identifier componentId) {
        return rules.getOrDefault(componentId, OWNER);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ComponentRuleSet other && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }
}
