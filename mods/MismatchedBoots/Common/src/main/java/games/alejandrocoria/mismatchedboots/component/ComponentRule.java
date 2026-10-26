package games.alejandrocoria.mismatchedboots.component;

import java.util.Objects;

public record ComponentRule(ComponentPolicy policy, ComponentNumericKind number) {
    public ComponentRule {
        Objects.requireNonNull(policy, "policy");
        if (number != null && !policy.isNumeric()) {
            throw new IllegalArgumentException("A numeric kind requires a numeric policy");
        }
    }

    public static ComponentRule of(ComponentPolicy policy) {
        return new ComponentRule(policy, null);
    }

    public static ComponentRule numeric(ComponentPolicy policy, ComponentNumericKind number) {
        if (!Objects.requireNonNull(policy, "policy").isNumeric()) {
            throw new IllegalArgumentException("Not a numeric policy: " + policy);
        }
        return new ComponentRule(policy, Objects.requireNonNull(number, "number"));
    }
}
