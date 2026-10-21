package games.alejandrocoria.mismatchedboots.attribute;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeRuleDefaultsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void tableCoversEveryMinecraftAttributeInAuditedOrder() {
        Set<Identifier> registeredMinecraftAttributes = BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .filter(id -> id.getNamespace().equals("minecraft"))
                .collect(Collectors.toSet());

        List<Identifier> defaultMinecraftAttributes = AttributeRuleDefaults.rules().keySet().stream()
                .filter(id -> id.getNamespace().equals("minecraft"))
                .toList();
        assertEquals(registeredMinecraftAttributes, Set.copyOf(defaultMinecraftAttributes));
        assertEquals(List.of(
                minecraft("armor"),
                minecraft("armor_toughness"),
                minecraft("attack_damage"),
                minecraft("attack_knockback"),
                minecraft("attack_speed"),
                minecraft("block_break_speed"),
                minecraft("block_interaction_range"),
                minecraft("burning_time"),
                minecraft("camera_distance"),
                minecraft("explosion_knockback_resistance"),
                minecraft("entity_interaction_range"),
                minecraft("fall_damage_multiplier"),
                minecraft("flying_speed"),
                minecraft("gravity"),
                minecraft("jump_strength"),
                minecraft("knockback_resistance"),
                minecraft("luck"),
                minecraft("max_absorption"),
                minecraft("max_health"),
                minecraft("mining_efficiency"),
                minecraft("movement_efficiency"),
                minecraft("movement_speed"),
                minecraft("oxygen_bonus"),
                minecraft("safe_fall_distance"),
                minecraft("scale"),
                minecraft("sneaking_speed"),
                minecraft("submerged_mining_speed"),
                minecraft("sweeping_damage_ratio"),
                minecraft("water_movement_efficiency"),
                minecraft("step_height"),
                minecraft("follow_range"),
                minecraft("spawn_reinforcements"),
                minecraft("tempt_range"),
                minecraft("waypoint_receive_range"),
                minecraft("waypoint_transmit_range")),
                defaultMinecraftAttributes);
    }

    @Test
    void optionalModDefaultsAreExplicitAndDoNotRequireRegisteredAttributes() {
        Map<Identifier, AttributePolicy> optionalRules = AttributeRuleDefaults.rules().entrySet().stream()
                .filter(entry -> !entry.getKey().getNamespace().equals("minecraft"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(Map.of(
                Identifier.parse("neoforge:swim_speed"), AttributePolicy.AVERAGE,
                Identifier.parse("neoforge:nametag_distance"), AttributePolicy.AVERAGE,
                Identifier.parse("neoforge:creative_flight"), AttributePolicy.SEPARATE,
                Identifier.parse("silentgear:magic_armor"), AttributePolicy.AVERAGE), optionalRules);
        optionalRules.keySet().forEach(id -> assertFalse(BuiltInRegistries.ATTRIBUTE.containsKey(id)));
    }

    @Test
    void defaultsDistinguishContinuousThresholdAndContextualAttributes() {
        AttributeRuleSet rules = AttributeRuleDefaults.ruleSet();

        assertEquals(AttributePolicy.AVERAGE, rules.policyFor(minecraft("armor")));
        assertEquals(AttributePolicy.AVERAGE, rules.policyFor(minecraft("movement_speed")));
        assertEquals(AttributePolicy.MAXIMUM, rules.policyFor(minecraft("step_height")));
        assertEquals(AttributePolicy.SEPARATE, rules.policyFor(minecraft("follow_range")));
        assertEquals(AttributePolicy.SEPARATE, rules.policyFor(minecraft("waypoint_receive_range")));
        assertEquals(AttributePolicy.SEPARATE, rules.fallback());
    }

    @Test
    void unlistedRegisteredAttributeUsesFallbackUnlessOverridden() {
        Identifier id = Identifier.fromNamespaceAndPath("test", "heat_resistance");
        MappedRegistry<Attribute> registry = new MappedRegistry<>(Registries.ATTRIBUTE, Lifecycle.stable());
        Holder<Attribute> registered = registry.register(
                ResourceKey.create(Registries.ATTRIBUTE, id),
                new RangedAttribute("attribute.test.heat_resistance", 0.0, -100.0, 100.0),
                RegistrationInfo.BUILT_IN);
        registry.freeze();
        assertEquals(id, registered.unwrapKey().orElseThrow().identifier());

        assertEquals(AttributePolicy.SEPARATE, AttributeRuleDefaults.ruleSet().policyFor(id));
        AttributeRuleSet overridden = new AttributeRuleSet(
                AttributePolicy.SEPARATE, Map.of(id, AttributePolicy.MAXIMUM));
        assertEquals(AttributePolicy.MAXIMUM, overridden.policyFor(id));
    }

    @Test
    void policyNamesAreStrictAndRuleSetsAreImmutableDefensiveCopies() {
        for (AttributePolicy policy : AttributePolicy.values()) {
            assertEquals(policy, AttributePolicy.fromSerializedName(policy.serializedName()).orElseThrow());
        }
        assertFalse(AttributePolicy.fromSerializedName("Average").isPresent());
        assertFalse(AttributePolicy.fromSerializedName("separate_half").isPresent());

        Identifier id = minecraft("armor");
        LinkedHashMap<Identifier, AttributePolicy> source = new LinkedHashMap<>();
        source.put(id, AttributePolicy.AVERAGE);
        AttributeRuleSet rules = new AttributeRuleSet(AttributePolicy.SEPARATE, source);
        source.put(id, AttributePolicy.REJECT);

        assertEquals(AttributePolicy.AVERAGE, rules.policyFor(id));
        assertThrows(UnsupportedOperationException.class,
                () -> rules.rules().put(minecraft("luck"), AttributePolicy.COMBINE));
    }

    @Test
    void sharedStateInstallsAndRestoresWholeImmutableSnapshots() {
        AttributeRuleSet custom = new AttributeRuleSet(
                AttributePolicy.REJECT, Map.of(minecraft("armor"), AttributePolicy.MAXIMUM));
        try {
            AttributeRuleState.install(custom);
            assertSame(custom, AttributeRuleState.current());
        } finally {
            AttributeRuleState.restoreDefaults();
        }
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
    }

    private static Identifier minecraft(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }
}
