package games.alejandrocoria.mismatchedboots.component;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Complete immutable component-composition state. */
public record ComponentRules(
        DurabilityPolicy durability,
        RepairCostPolicy repairCost,
        Map<Identifier, ComponentRule> declarations,
        ComponentRuleSet components) {
    public ComponentRules {
        Objects.requireNonNull(durability, "durability");
        Objects.requireNonNull(repairCost, "repairCost");
        LinkedHashMap<Identifier, ComponentRule> copy = new LinkedHashMap<>();
        Objects.requireNonNull(declarations, "declarations");
        declarations.forEach((id, rule) -> copy.put(
                Objects.requireNonNull(id, "component ID"),
                Objects.requireNonNull(rule, "component rule")));
        declarations = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(components, "components");
    }

    public ComponentRules(
            DurabilityPolicy durability,
            RepairCostPolicy repairCost,
            ComponentRuleSet components) {
        this(durability, repairCost, components.rules(), components);
    }
}
