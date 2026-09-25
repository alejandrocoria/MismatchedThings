package games.alejandrocoria.mismatchedboots.component;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentRuleDefaults {
    private static final Map<Identifier, ComponentRule> RULES = createRules();
    private static final ComponentRuleSet RULE_SET = new ComponentRuleSet(RULES);
    private static final ComponentRules STATE = new ComponentRules(
            DurabilityPolicy.AVERAGE, RepairCostPolicy.MAXIMUM, RULE_SET);

    private ComponentRuleDefaults() {
    }

    public static Map<Identifier, ComponentRule> rules() {
        return RULES;
    }

    public static ComponentRuleSet ruleSet() {
        return RULE_SET;
    }

    public static ComponentRules state() {
        return STATE;
    }

    private static Map<Identifier, ComponentRule> createRules() {
        LinkedHashMap<Identifier, ComponentRule> rules = new LinkedHashMap<>();
        put(rules, "damage_resistant", ComponentPolicy.INTERSECTION);
        put(rules, "unbreakable", ComponentPolicy.SAME);
        put(rules, "glider", ComponentPolicy.SAME);
        put(rules, "enchantable", ComponentPolicy.MINIMUM);
        put(rules, "rarity", ComponentPolicy.SAME);
        put(rules, "tooltip_display", ComponentPolicy.SAME);
        put(rules, "enchantment_glint_override", ComponentPolicy.SAME);
        put(rules, "lore", ComponentPolicy.OMIT);
        put(rules, "trim", ComponentPolicy.OMIT);
        put(rules, "dyed_color", ComponentPolicy.OMIT);
        put(rules, "custom_model_data", ComponentPolicy.OMIT);
        return Collections.unmodifiableMap(rules);
    }

    private static void put(Map<Identifier, ComponentRule> rules, String path, ComponentPolicy policy) {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
        if (rules.put(id, ComponentRule.of(policy)) != null) {
            throw new IllegalStateException("Duplicate built-in component rule: " + id);
        }
    }
}
