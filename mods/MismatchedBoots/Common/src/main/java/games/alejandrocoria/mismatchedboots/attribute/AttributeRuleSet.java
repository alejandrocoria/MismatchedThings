package games.alejandrocoria.mismatchedboots.attribute;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AttributeRuleSet {
    private final AttributePolicy fallback;
    private final Map<Identifier, AttributePolicy> rules;

    public AttributeRuleSet(AttributePolicy fallback, Map<Identifier, AttributePolicy> rules) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        LinkedHashMap<Identifier, AttributePolicy> copiedRules = new LinkedHashMap<>();
        rules.forEach((id, policy) -> copiedRules.put(
                Objects.requireNonNull(id, "attribute ID"),
                Objects.requireNonNull(policy, "attribute policy")));
        this.rules = Collections.unmodifiableMap(copiedRules);
    }

    public AttributePolicy fallback() {
        return fallback;
    }

    public Map<Identifier, AttributePolicy> rules() {
        return rules;
    }

    public AttributePolicy policyFor(Identifier attributeId) {
        return rules.getOrDefault(attributeId, fallback);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AttributeRuleSet other)) {
            return false;
        }
        return fallback == other.fallback && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fallback, rules);
    }

    @Override
    public String toString() {
        return "AttributeRuleSet[fallback=" + fallback + ", rules=" + rules + "]";
    }
}
