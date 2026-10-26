package games.alejandrocoria.mismatchedboots.attribute;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttributeRuleDefaults {
    public static final AttributePolicy FALLBACK = AttributePolicy.SEPARATE;

    private static final List<String> AVERAGED_ATTRIBUTES = List.of(
            "armor",
            "armor_toughness",
            "attack_damage",
            "attack_knockback",
            "attack_speed",
            "block_break_speed",
            "block_interaction_range",
            "burning_time",
            "camera_distance",
            "explosion_knockback_resistance",
            "entity_interaction_range",
            "fall_damage_multiplier",
            "flying_speed",
            "gravity",
            "jump_strength",
            "knockback_resistance",
            "luck",
            "max_absorption",
            "max_health",
            "mining_efficiency",
            "movement_efficiency",
            "movement_speed",
            "oxygen_bonus",
            "safe_fall_distance",
            "scale",
            "sneaking_speed",
            "submerged_mining_speed",
            "sweeping_damage_ratio",
            "water_movement_efficiency");
    private static final List<String> SEPARATE_ATTRIBUTES = List.of(
            "follow_range",
            "spawn_reinforcements",
            "tempt_range",
            "waypoint_receive_range",
            "waypoint_transmit_range");
    private static final Map<Identifier, AttributePolicy> RULES = createRules();
    private static final AttributeRuleSet RULE_SET = new AttributeRuleSet(FALLBACK, RULES);

    private AttributeRuleDefaults() {
    }

    public static Map<Identifier, AttributePolicy> rules() {
        return RULES;
    }

    public static AttributeRuleSet ruleSet() {
        return RULE_SET;
    }

    private static Map<Identifier, AttributePolicy> createRules() {
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>();
        AVERAGED_ATTRIBUTES.forEach(path -> put(rules, "minecraft", path, AttributePolicy.AVERAGE));
        put(rules, "minecraft", "step_height", AttributePolicy.MAXIMUM);
        SEPARATE_ATTRIBUTES.forEach(path -> put(rules, "minecraft", path, AttributePolicy.SEPARATE));
        put(rules, "neoforge", "swim_speed", AttributePolicy.AVERAGE);
        put(rules, "neoforge", "nametag_distance", AttributePolicy.AVERAGE);
        put(rules, "neoforge", "creative_flight", AttributePolicy.SEPARATE);
        put(rules, "silentgear", "magic_armor", AttributePolicy.AVERAGE);
        return Collections.unmodifiableMap(rules);
    }

    private static void put(
            Map<Identifier, AttributePolicy> rules, String namespace, String path, AttributePolicy policy) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        if (rules.put(id, policy) != null) {
            throw new IllegalStateException("Duplicate built-in attribute rule: " + id);
        }
    }
}
