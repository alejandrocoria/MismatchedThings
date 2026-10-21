package games.alejandrocoria.mismatchedboots.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentProjectionTest {
    private static final DataComponentType<Integer> INTEGER = DataComponentType.<Integer>builder()
            .persistent(Codec.INT).build();
    private static final DataComponentType<Long> LONG = DataComponentType.<Long>builder()
            .persistent(Codec.LONG).build();
    private static final DataComponentType<String> STRING = DataComponentType.<String>builder()
            .persistent(Codec.STRING).build();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultsAndRuleSetAreExplicitAndImmutable() {
        ComponentRuleSet defaults = ComponentRuleDefaults.ruleSet();
        assertEquals(ComponentPolicy.INTERSECTION, defaults.ruleFor(minecraft("damage_resistant")).policy());
        assertEquals(ComponentPolicy.MINIMUM, defaults.ruleFor(minecraft("enchantable")).policy());
        assertEquals(ComponentPolicy.OMIT, defaults.ruleFor(minecraft("trim")).policy());
        assertEquals(ComponentPolicy.OWNER, defaults.ruleFor(minecraft("custom_data")).policy());
        assertEquals(ComponentPolicy.OWNER, defaults.ruleFor(testId("unknown")).policy());
        assertEquals(ComponentPolicy.OMIT, defaults.ruleFor(Identifier.parse("aether_ii:charms")).policy());
        defaults.rules().keySet().stream().filter(id -> id.getNamespace().equals("minecraft")).forEach(id ->
                assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).isPresent(), id.toString()));

        Map<Identifier, ComponentRule> source = new LinkedHashMap<>();
        source.put(testId("energy"), ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG));
        ComponentRuleSet rules = new ComponentRuleSet(source);
        source.clear();
        assertEquals(ComponentNumericKind.LONG, rules.ruleFor(testId("energy")).number());
        assertThrows(UnsupportedOperationException.class,
                () -> rules.rules().put(testId("other"), ComponentRule.of(ComponentPolicy.OWNER)));
        assertThrows(IllegalArgumentException.class,
                () -> ComponentRule.numeric(ComponentPolicy.SHARE, ComponentNumericKind.INT));
        assertEquals(ComponentPolicy.SAME, ComponentPolicy.fromSerializedName("same").orElseThrow());
        assertTrue(ComponentPolicy.fromSerializedName("SAME").isEmpty());
        assertEquals(ComponentNumericKind.INT, ComponentNumericKind.fromSerializedName("int").orElseThrow());
    }

    @Test
    void fullProjectionKeepsOwnerFallbackAndSkipsFixedFields() {
        ItemLore firstLore = new ItemLore(List.of(Component.literal("first")));
        ItemLore secondLore = new ItemLore(List.of(Component.literal("second")));
        CompoundTag firstData = new CompoundTag();
        firstData.putString("owner", "first");
        CompoundTag secondData = new CompoundTag();
        secondData.putString("owner", "second");
        ItemStack first = stack(DataComponentMap.builder()
                .set(DataComponents.RARITY, Rarity.RARE)
                .set(DataComponents.LORE, firstLore)
                .set(DataComponents.REPAIR_COST, 8)
                .set(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .set(DataComponents.ENCHANTABLE, new Enchantable(25))
                .set(DataComponents.CUSTOM_DATA, CustomData.of(firstData))
                .build());
        ItemStack second = stack(DataComponentMap.builder()
                .set(DataComponents.RARITY, Rarity.RARE)
                .set(DataComponents.LORE, secondLore)
                .set(DataComponents.REPAIR_COST, 2)
                .set(DataComponents.ENCHANTABLE, new Enchantable(9))
                .set(DataComponents.CUSTOM_DATA, CustomData.of(secondData))
                .build());

        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();
        ComponentProjection.Result result = ComponentProjection.project(
                first, second, ComponentRuleDefaults.ruleSet());
        assertEquals(Rarity.RARE, result.primary().get(DataComponents.RARITY));
        assertEquals(Rarity.RARE, result.inverse().get(DataComponents.RARITY));
        assertNull(result.primary().get(DataComponents.LORE));
        assertNull(result.inverse().get(DataComponents.LORE));
        assertNull(result.primary().get(DataComponents.UNBREAKABLE));
        assertNull(result.inverse().get(DataComponents.UNBREAKABLE));
        assertEquals(new Enchantable(9), result.primary().get(DataComponents.ENCHANTABLE));
        assertEquals(new Enchantable(9), result.inverse().get(DataComponents.ENCHANTABLE));
        assertFalse(result.primary().has(DataComponents.REPAIR_COST));
        assertFalse(result.inverse().has(DataComponents.REPAIR_COST));
        assertEquals(first.get(DataComponents.CUSTOM_DATA), result.primary().get(DataComponents.CUSTOM_DATA));
        assertEquals(second.get(DataComponents.CUSTOM_DATA), result.inverse().get(DataComponents.CUSTOM_DATA));
        assertEquals(firstLore, first.get(DataComponents.LORE));
        assertEquals(secondLore, second.get(DataComponents.LORE));
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
        assertTrue(result.managedTypes().contains(DataComponents.LORE));
        assertTrue(result.managedTypes().contains(DataComponents.TRIM)); // Explicit rule, absent on both sources.
        assertFalse(result.managedTypes().contains(DataComponents.REPAIR_COST));
        assertThrows(UnsupportedOperationException.class, () -> result.managedTypes().clear());
        ComponentProjection.Result swapped = ComponentProjection.project(
                second, first, ComponentRuleDefaults.ruleSet());
        assertEquals(result.managedTypes(), swapped.managedTypes());
        assertTrue(ItemStack.matches(stack(result.primary()), stack(swapped.inverse())));
        assertTrue(ItemStack.matches(stack(result.inverse()), stack(swapped.primary())));

        second.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        ComponentProjection.Result bothUnbreakable = ComponentProjection.project(
                first, second, ComponentRuleDefaults.ruleSet());
        assertEquals(Unit.INSTANCE, bothUnbreakable.primary().get(DataComponents.UNBREAKABLE));
        assertEquals(Unit.INSTANCE, bothUnbreakable.inverse().get(DataComponents.UNBREAKABLE));
    }

    @Test
    void opaqueRulesCoverBothOrientationsMissingValuesAndConflicts() {
        assertEquals(new ComponentProjection.Values<>("left", "right"), values(
                STRING, "left", "right", ComponentPolicy.OWNER));
        assertEquals(new ComponentProjection.Values<>(null, null), values(
                STRING, "left", "right", ComponentPolicy.OMIT));
        assertEquals(new ComponentProjection.Values<>("same", "same"), values(
                STRING, "same", "same", ComponentPolicy.SAME));
        assertEquals(new ComponentProjection.Values<>(null, null), values(
                STRING, "left", "right", ComponentPolicy.SAME));
        assertEquals(new ComponentProjection.Values<>(null, null), values(
                STRING, "left", null, ComponentPolicy.SAME));
        assertEquals(new ComponentProjection.Values<>("left", "left"), values(
                STRING, "left", null, ComponentPolicy.SHARE));
        assertEquals(new ComponentProjection.Values<>("right", "right"), values(
                STRING, null, "right", ComponentPolicy.SHARE));
        assertEquals(new ComponentProjection.Values<>("left", "right"), values(
                STRING, "left", "right", ComponentPolicy.SHARE));
    }

    @Test
    void integerArithmeticIsOverflowSafeAndValidatesCodec() {
        assertEquals(new ComponentProjection.Values<>(-1, -1), ComponentProjection.projectValues(
                INTEGER, Integer.MIN_VALUE, Integer.MAX_VALUE,
                ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertEquals(new ComponentProjection.Values<>(Long.MAX_VALUE, Long.MAX_VALUE),
                ComponentProjection.projectValues(LONG, Long.MAX_VALUE, Long.MAX_VALUE,
                        ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG)));
        assertEquals(new ComponentProjection.Values<>(-1L, -1L),
                ComponentProjection.projectValues(LONG, Long.MIN_VALUE, Long.MAX_VALUE,
                        ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG)));
        assertEquals(new ComponentProjection.Values<>(4, 4), ComponentProjection.projectValues(
                INTEGER, 4, 9, ComponentRule.numeric(ComponentPolicy.MINIMUM, ComponentNumericKind.INT)));
        assertEquals(new ComponentProjection.Values<>(9, 9), ComponentProjection.projectValues(
                INTEGER, 4, 9, ComponentRule.numeric(ComponentPolicy.MAXIMUM, ComponentNumericKind.INT)));

        DataComponentType<Integer> limited = DataComponentType.<Integer>builder()
                .persistent(Codec.intRange(0, 10)).build();
        assertThrows(IllegalArgumentException.class, () -> ComponentProjection.projectValues(
                limited, 10, 100, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        // The mean is valid, but invalid sources must not be silently repaired by arithmetic.
        assertThrows(IllegalArgumentException.class, () -> ComponentProjection.projectValues(
                limited, -2, 12, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertThrows(IllegalArgumentException.class, () -> ComponentProjection.projectValues(
                limited, null, 12, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertThrows(IllegalArgumentException.class, () -> ComponentProjection.projectValues(
                limited, -2, null, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        DataComponentType<Integer> even = DataComponentType.<Integer>builder()
                .persistent(Codec.INT.validate(value -> value % 2 == 0
                        ? DataResult.success(value) : DataResult.error(() -> "Expected an even value"))).build();
        assertEquals(new ComponentProjection.Values<>(2, 4), ComponentProjection.projectValues(
                even, 2, 4, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertEquals(new ComponentProjection.Values<>(4, null), ComponentProjection.projectValues(
                INTEGER, 4, null, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertEquals(new ComponentProjection.Values<>(4, 9), ComponentProjection.projectValues(
                INTEGER, 4, 9, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG)));
        assertEquals(new ComponentProjection.Values<>(4, 9), ComponentProjection.projectValues(
                INTEGER, 4, 9, ComponentRule.of(ComponentPolicy.AVERAGE)));
    }

    @Test
    void registeredComponentWithoutUsableNumericCodecKeepsOwnerValues() {
        assertTrue(DataComponents.ADDITIONAL_TRADE_COST.isTransient());
        assertNull(DataComponents.ADDITIONAL_TRADE_COST.codec());
        ItemStack first = stack(DataComponentMap.builder()
                .set(DataComponents.ADDITIONAL_TRADE_COST, 1).build());
        ItemStack second = stack(DataComponentMap.builder()
                .set(DataComponents.ADDITIONAL_TRADE_COST, 3).build());
        ComponentRuleSet rules = new ComponentRuleSet(Map.of(minecraft("additional_trade_cost"),
                ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));

        ComponentProjection.Result result = ComponentProjection.project(first, second, rules);
        assertEquals(1, result.primary().get(DataComponents.ADDITIONAL_TRADE_COST));
        assertEquals(3, result.inverse().get(DataComponents.ADDITIONAL_TRADE_COST));
    }

    @Test
    void enchantableUsesItsDedicatedPositiveIntegerAdapter() {
        ComponentRule minimum = ComponentRule.of(ComponentPolicy.MINIMUM);
        assertEquals(new ComponentProjection.Values<>(new Enchantable(9), new Enchantable(9)),
                ComponentProjection.projectValues(DataComponents.ENCHANTABLE,
                        new Enchantable(25), new Enchantable(9), minimum));
        assertEquals(new ComponentProjection.Values<>(null, null),
                ComponentProjection.projectValues(DataComponents.ENCHANTABLE,
                        new Enchantable(25), null, minimum));
        assertEquals(new ComponentProjection.Values<>(new Enchantable(9), new Enchantable(9)),
                ComponentProjection.projectValues(DataComponents.ENCHANTABLE,
                        new Enchantable(25), new Enchantable(9),
                        ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)));
        assertEquals(new ComponentProjection.Values<>(new Enchantable(25), new Enchantable(25)),
                values(DataComponents.ENCHANTABLE, new Enchantable(25), null, ComponentPolicy.SHARE));
    }

    @Test
    void ruleSupportResolvesDefaultsAndProtectsFixedFields() {
        assertEquals(ComponentRule.of(ComponentPolicy.SAME), ComponentRuleSupport.resolve(
                DataComponents.RARITY, ComponentRule.of(ComponentPolicy.INTERSECTION)).orElseThrow());
        assertEquals(ComponentRule.of(ComponentPolicy.OMIT), ComponentRuleSupport.resolve(
                DataComponents.LORE, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT))
                .orElseThrow());
        assertEquals(new ComponentProjection.Values<>(null, null), values(
                DataComponents.RARITY, Rarity.RARE, Rarity.COMMON, ComponentPolicy.INTERSECTION));
        assertEquals(ComponentRule.of(ComponentPolicy.MINIMUM), ComponentRuleSupport.resolve(
                DataComponents.ENCHANTABLE,
                ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)).orElseThrow());
        assertEquals(ComponentRule.of(ComponentPolicy.INTERSECTION), ComponentRuleSupport.resolve(
                DataComponents.DAMAGE_RESISTANT,
                ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT)).orElseThrow());
        assertTrue(ComponentRuleSupport.resolve(DataComponents.REPAIR_COST,
                ComponentRule.of(ComponentPolicy.OMIT)).isEmpty());
        ComponentProjection.Result result = ComponentProjection.project(
                stack(DataComponentMap.builder().set(DataComponents.REPAIR_COST, 8).build()),
                stack(DataComponentMap.EMPTY),
                new ComponentRuleSet(Map.of(minecraft("repair_cost"), ComponentRule.of(ComponentPolicy.SHARE))));
        assertFalse(result.managedTypes().contains(DataComponents.REPAIR_COST));
        assertFalse(result.primary().has(DataComponents.REPAIR_COST));
    }

    @Test
    void swappingInputsSwapsOpaqueAndNumericOutputsIncludingAbsence() {
        for (ComponentPolicy policy : List.of(ComponentPolicy.OWNER, ComponentPolicy.OMIT,
                ComponentPolicy.SAME, ComponentPolicy.SHARE)) {
            for (String first : Arrays.asList(null, "a", "b")) {
                for (String second : Arrays.asList(null, "a", "b")) {
                    assertSwap(STRING, first, second, ComponentRule.of(policy));
                }
            }
        }
        for (ComponentPolicy policy : List.of(ComponentPolicy.AVERAGE, ComponentPolicy.MINIMUM, ComponentPolicy.MAXIMUM)) {
            for (Integer first : Arrays.asList(null, Integer.MIN_VALUE, -3, 0, Integer.MAX_VALUE)) {
                for (Integer second : Arrays.asList(null, Integer.MIN_VALUE, -3, 0, Integer.MAX_VALUE)) {
                    assertSwap(INTEGER, first, second, ComponentRule.numeric(policy, ComponentNumericKind.INT));
                }
            }
        }
    }

    @Test
    void symmetricDamagePoliciesProduceCanonicalListsWhenInputsAreSwapped() {
        Holder<DamageType> fire = damage("fire");
        Holder<DamageType> cactus = damage("cactus");
        DamageResistant first = new DamageResistant(HolderSet.direct(fire, cactus, fire));
        DamageResistant second = new DamageResistant(HolderSet.direct(cactus, fire));
        for (ComponentPolicy policy : List.of(ComponentPolicy.SAME, ComponentPolicy.SHARE, ComponentPolicy.INTERSECTION)) {
            assertSwap(DataComponents.DAMAGE_RESISTANT, first, second, ComponentRule.of(policy));
            var result = values(DataComponents.DAMAGE_RESISTANT, first, second, policy);
            assertEquals(List.of(cactus, fire), result.primary().types().stream().toList());
        }
        assertSwap(DataComponents.DAMAGE_RESISTANT, first, null, ComponentRule.of(ComponentPolicy.SHARE));
        // Owner policies preserve the source representation instead of normalizing it.
        assertSame(first, values(DataComponents.DAMAGE_RESISTANT, first, second, ComponentPolicy.OWNER).primary());
    }

    private static <T> void assertSwap(DataComponentType<T> type, T first, T second, ComponentRule rule) {
        var forward = ComponentProjection.projectValues(type, first, second, rule);
        var backward = ComponentProjection.projectValues(type, second, first, rule);
        assertEquals(forward.primary(), backward.inverse(), rule.toString());
        assertEquals(forward.inverse(), backward.primary(), rule.toString());
    }

    @Test
    @SuppressWarnings("deprecation")
    void namedDamageSetsCompareByTagIdAndUnrepresentableOverlapsAreOmitted() {
        HolderOwner<DamageType> owner = new HolderOwner<>() {
        };
        TagKey<DamageType> fire = tag("fire");
        DamageResistant first = new DamageResistant(HolderSet.emptyNamed(owner, fire));
        DamageResistant sameTag = new DamageResistant(HolderSet.emptyNamed(owner, fire));
        DamageResistant cactus = new DamageResistant(HolderSet.emptyNamed(owner, tag("cactus")));

        assertFalse(first.equals(sameTag));
        assertTrue(ComponentProjection.equivalentValues(DataComponents.DAMAGE_RESISTANT, first, sameTag));
        assertTrue(ComponentProjection.equivalentValues(DataComponents.DAMAGE_RESISTANT, null, null));
        assertFalse(ComponentProjection.equivalentValues(DataComponents.DAMAGE_RESISTANT, first, cactus));
        assertSame(first, ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                first, sameTag, ComponentRule.of(ComponentPolicy.SAME)).primary());
        var overlap = ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                first, sameTag, ComponentRule.of(ComponentPolicy.INTERSECTION));
        assertSame(first, overlap.primary());
        assertSame(first, overlap.inverse());
        assertNull(ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                first, cactus, ComponentRule.of(ComponentPolicy.INTERSECTION)).primary());
        assertNull(ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                first, null, ComponentRule.of(ComponentPolicy.INTERSECTION)).inverse());
        assertEquals(new ComponentProjection.Values<>(first, cactus),
                ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                        first, cactus, ComponentRule.of(ComponentPolicy.SHARE)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void directDamageSetsIntersectByRegisteredIdsRatherThanHolderIdentity() {
        Holder<DamageType> fireLeft = damage("fire");
        Holder<DamageType> cactusLeft = damage("cactus");
        Holder<DamageType> cactusRight = damage("cactus");
        Holder<DamageType> fallRight = damage("fall");
        DamageResistant left = new DamageResistant(HolderSet.direct(fireLeft, cactusLeft));
        DamageResistant right = new DamageResistant(HolderSet.direct(cactusRight, fallRight));

        var overlap = ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                left, right, ComponentRule.of(ComponentPolicy.INTERSECTION));
        assertEquals(1, overlap.primary().types().size());
        assertEquals(cactusLeft.unwrapKey(), overlap.primary().types().get(0).unwrapKey());
        assertEquals(overlap.primary(), overlap.inverse());
        assertNull(ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                left, new DamageResistant(HolderSet.direct(fallRight)),
                ComponentRule.of(ComponentPolicy.INTERSECTION)).primary());
        assertNull(ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                left, new DamageResistant(HolderSet.direct(Holder.direct(new DamageType("unkeyed", 0.1F)))),
                ComponentRule.of(ComponentPolicy.INTERSECTION)).primary());
        assertNull(ComponentProjection.projectValues(DataComponents.DAMAGE_RESISTANT,
                left, new DamageResistant(HolderSet.emptyNamed(new HolderOwner<>() {
                }, tag("fire"))), ComponentRule.of(ComponentPolicy.INTERSECTION)).primary());
    }

    @Test
    void unsupportedIntersectionFallsBackToOwners() {
        assertEquals(new ComponentProjection.Values<>("left", "right"), ComponentProjection.projectValues(
                STRING, "left", "right", ComponentRule.of(ComponentPolicy.INTERSECTION)));
    }

    private static <T> ComponentProjection.Values<T> values(
            DataComponentType<T> type, T first, T second, ComponentPolicy policy) {
        return ComponentProjection.projectValues(type, first, second, ComponentRule.of(policy));
    }

    private static ItemStack stack(DataComponentMap components) {
        return new ItemStack(Holder.direct(Items.IRON_BOOTS, components), 1);
    }

    private static Identifier minecraft(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }

    private static TagKey<DamageType> tag(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, testId(path));
    }

    private static Holder<DamageType> damage(String path) {
        MappedRegistry<DamageType> registry = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        Holder<DamageType> holder = registry.register(
                ResourceKey.create(Registries.DAMAGE_TYPE, testId(path)),
                new DamageType(path, 0.1F), RegistrationInfo.BUILT_IN);
        registry.freeze();
        return holder;
    }
}
