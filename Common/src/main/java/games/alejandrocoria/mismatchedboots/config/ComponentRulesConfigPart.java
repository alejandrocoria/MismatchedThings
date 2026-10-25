package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSupport;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class ComponentRulesConfigPart implements DynamicConfigPart {
    private static final String DURABILITY = "durability";
    private static final String REPAIR_COST = "repair_cost";
    private static final String COMPONENTS = "components";
    private static final String POLICY = "policy";
    private static final String NUMBER = "number";
    private static final Set<String> ROOT_KEYS = Set.of(DURABILITY, REPAIR_COST, COMPONENTS);
    private static final ComponentRule OWNER = ComponentRule.of(ComponentPolicy.OWNER);

    private ComponentRules resolved = ComponentRuleDefaults.state();

    @Override
    public boolean load(CommentedConfig root) {
        boolean dirty = root.valueMap().keySet().stream().anyMatch(key -> !ROOT_KEYS.contains(key));
        Parsed<DurabilityPolicy> durability = loadDedicated(
                root.valueMap().get(DURABILITY), DurabilityPolicy.AVERAGE,
                DurabilityPolicy::fromSerializedName, DURABILITY);
        Parsed<RepairCostPolicy> repairCost = loadDedicated(
                root.valueMap().get(REPAIR_COST), RepairCostPolicy.MAXIMUM,
                RepairCostPolicy::fromSerializedName, REPAIR_COST);
        dirty |= durability.dirty() | repairCost.dirty();

        LinkedHashMap<Identifier, ComponentRule> declarations =
                new LinkedHashMap<>(ComponentRuleDefaults.rules());
        Set<Identifier> presentDefaults = new HashSet<>();
        Object rawComponents = root.valueMap().get(COMPONENTS);
        if (rawComponents instanceof CommentedConfig components) {
            for (Map.Entry<String, Object> entry : components.valueMap().entrySet()) {
                String rawId = entry.getKey();
                Identifier id = parseExplicitIdentifier(rawId);
                if (id == null) {
                    Constants.LOG.warn("Discarding invalid component ID '{}' from the MismatchedBoots config", rawId);
                    dirty = true;
                    continue;
                }
                presentDefaults.add(id);
                ComponentRule fallback = ComponentRuleDefaults.rules().getOrDefault(id, OWNER);
                Parsed<ComponentRule> parsed = loadRule(entry.getValue(), id, fallback);
                dirty |= parsed.dirty();
                declarations.put(id, parsed.value());
            }
        } else {
            dirty = true;
        }
        if (!presentDefaults.containsAll(ComponentRuleDefaults.rules().keySet())) {
            dirty = true;
        }

        LinkedHashMap<Identifier, ComponentRule> orderedDeclarations = order(declarations);
        LinkedHashMap<Identifier, ComponentRule> effective = new LinkedHashMap<>();
        List<Identifier> discarded = new ArrayList<>();
        for (Map.Entry<Identifier, ComponentRule> entry : orderedDeclarations.entrySet()) {
            var registered = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(entry.getKey());
            if (registered.isEmpty()) {
                continue;
            }
            var selected = ComponentRuleSupport.resolve(registered.orElseThrow(), entry.getValue());
            if (selected.isEmpty()) {
                Constants.LOG.warn("Discarding component rule '{}' because the field is fixed by MismatchedBoots",
                                   entry.getKey());
                discarded.add(entry.getKey());
                dirty = true;
                continue;
            }
            ComponentRule rule = selected.orElseThrow();
            if (!rule.equals(entry.getValue())) {
                Constants.LOG.warn("Normalizing unsupported component rule '{}' to {}", entry.getKey(), rule);
                entry.setValue(rule);
                dirty = true;
            }
            effective.put(entry.getKey(), rule);
        }
        discarded.forEach(orderedDeclarations::remove);
        resolved = new ComponentRules(
                durability.value(), repairCost.value(), orderedDeclarations, new ComponentRuleSet(effective));
        return dirty;
    }

    @Override
    public void save(CommentedConfig root) {
        CommentedConfig durability = root.createSubConfig();
        durability.set(POLICY, resolved.durability().serializedName());
        root.set(DURABILITY, durability);
        root.setComment(DURABILITY, "Maximum and remaining-durability composition: average or separate.");

        CommentedConfig repairCost = root.createSubConfig();
        repairCost.set(POLICY, resolved.repairCost().serializedName());
        root.set(REPAIR_COST, repairCost);
        root.setComment(REPAIR_COST, "Prior-work cost composition: owner, average, minimum, or maximum.");

        CommentedConfig components = root.createSubConfig();
        for (Map.Entry<Identifier, ComponentRule> entry : resolved.declarations().entrySet()) {
            CommentedConfig category = components.createSubConfig();
            category.set(POLICY, entry.getValue().policy().serializedName());
            if (entry.getValue().number() != null) {
                category.set(NUMBER, entry.getValue().number().serializedName());
            }
            components.set(List.of(entry.getKey().toString()), category);
        }
        root.set(COMPONENTS, components);
        root.setComment(COMPONENTS, "Root data-component composition rules keyed by full component ID.");
    }

    @Override
    public void reset() {
        resolved = ComponentRuleDefaults.state();
    }

    ComponentRules resolved() {
        return resolved;
    }

    private static <T> Parsed<T> loadDedicated(
            Object rawSection,
            T fallback,
            Function<String, java.util.Optional<T>> parser,
            String sectionName) {
        if (rawSection == null) {
            return new Parsed<>(fallback, true);
        }
        if (!(rawSection instanceof CommentedConfig section)
                || section.valueMap().keySet().stream().anyMatch(key -> !POLICY.equals(key))) {
            Constants.LOG.warn("Cleaning invalid '{}' component-rule section", sectionName);
            return new Parsed<>(fallback, true);
        }
        Object rawPolicy = section.valueMap().get(POLICY);
        T policy = rawPolicy instanceof String value ? parser.apply(value).orElse(null) : null;
        if (policy == null) {
            Constants.LOG.warn("Cleaning invalid policy in '{}' component-rule section", sectionName);
            return new Parsed<>(fallback, true);
        }
        return new Parsed<>(policy, false);
    }

    private static Parsed<ComponentRule> loadRule(Object rawCategory, Identifier id, ComponentRule fallback) {
        if (!(rawCategory instanceof CommentedConfig category)) {
            Constants.LOG.warn("Cleaning component rule '{}' because it is not a TOML table", id);
            return new Parsed<>(fallback, true);
        }
        boolean dirty = category.valueMap().keySet().stream()
                                .anyMatch(key -> !POLICY.equals(key) && !NUMBER.equals(key));
        if (dirty) {
            warnRule(id);
            return new Parsed<>(fallback, true);
        }
        Object rawPolicy = category.valueMap().get(POLICY);
        ComponentPolicy policy = rawPolicy instanceof String value
                                 ? ComponentPolicy.fromSerializedName(value).orElse(null) : null;
        if (policy == null) {
            warnRule(id);
            return new Parsed<>(fallback, true);
        }

        Object rawNumber = category.valueMap().get(NUMBER);
        ComponentNumericKind number = rawNumber instanceof String value
                                      ? ComponentNumericKind.fromSerializedName(value).orElse(null) : null;
        DataComponentType<?> registered = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
        boolean dedicatedNumber = registered == DataComponents.ENCHANTABLE;
        ComponentRule rule;
        if (policy.isNumeric() && !dedicatedNumber) {
            if (number == null) {
                warnRule(id);
                return new Parsed<>(fallback, true);
            }
            rule = ComponentRule.numeric(policy, number);
        } else {
            if (rawNumber != null) {
                warnRule(id);
                return new Parsed<>(fallback, true);
            }
            rule = ComponentRule.of(policy);
        }
        return new Parsed<>(rule, false);
    }

    private static void warnRule(Identifier id) {
        Constants.LOG.warn("Cleaning invalid or unknown values from component rule '{}'", id);
    }

    private static LinkedHashMap<Identifier, ComponentRule> order(Map<Identifier, ComponentRule> declarations) {
        LinkedHashMap<Identifier, ComponentRule> ordered = new LinkedHashMap<>();
        ComponentRuleDefaults.rules().keySet().forEach(id -> {
            if (declarations.containsKey(id)) {
                ordered.put(id, declarations.get(id));
            }
        });
        declarations.entrySet().stream()
                    .filter(entry -> !ComponentRuleDefaults.rules().containsKey(entry.getKey()))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    private static Identifier parseExplicitIdentifier(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            return null;
        }
        try {
            return Identifier.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record Parsed<T>(T value, boolean dirty) {
    }
}
