package games.alejandrocoria.mismatchedboots.component;

import java.util.Objects;

/** Complete immutable component-composition state. */
public record ComponentRules(
        DurabilityPolicy durability,
        RepairCostPolicy repairCost,
        ComponentRuleSet components) {
    public ComponentRules {
        Objects.requireNonNull(durability, "durability");
        Objects.requireNonNull(repairCost, "repairCost");
        Objects.requireNonNull(components, "components");
    }
}
