package games.alejandrocoria.mismatchedboots.composition;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeComposerTest {
    private static final Identifier SHARED_ID = id("shared");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void candidateNormalizesVanillaIronAndGoldArmorToOneModifier() {
        ItemStack iron = vanillaBoots(Items.IRON_BOOTS, 2.0D);
        ItemStack gold = vanillaBoots(Items.GOLDEN_BOOTS, 1.0D);
        ItemAttributeModifiers.Entry ironArmor = modifiers(iron).modifiers().getFirst();
        ItemAttributeModifiers.Entry goldArmor = modifiers(gold).modifiers().getFirst();
        assertEquals(ironArmor.modifier().id(), goldArmor.modifier().id());

        AttributeComposer.Result result = AttributeComposer.compose(
                iron, gold, AttributeRuleDefaults.ruleSet());

        assertEquals(result.primary(), result.inverse());
        assertEquals(1, result.primary().modifiers().size());
        ItemAttributeModifiers.Entry composed = result.primary().modifiers().getFirst();
        assertEquals(Attributes.ARMOR, composed.attribute());
        assertEquals(ironArmor.modifier().id(), composed.modifier().id());
        assertEquals(1.5D, composed.modifier().amount());
        assertFalse(composed.modifier().id().toString().startsWith("mismatchedboots:attribute/"));
    }

    @ParameterizedTest
    @MethodSource("arithmeticCases")
    void appliesEveryArithmeticPolicyAcrossEveryOperation(
            AttributePolicy policy,
            AttributeModifier.Operation operation,
            double firstAmount,
            double secondAmount,
            double expectedAmount) {
        ItemStack first = stack(entry(SHARED_ID, firstAmount, operation));
        ItemStack second = stack(entry(SHARED_ID, secondAmount, operation));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(policy));

        assertEquals(expectedAmount, onlyAmount(result.primary()), 1.0E-12D);
        assertEquals(result.primary(), result.inverse());
    }

    static Stream<Arguments> arithmeticCases() {
        return Stream.of(AttributeModifier.Operation.values()).flatMap(operation -> {
            if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                return Stream.of(
                        Arguments.of(AttributePolicy.AVERAGE, operation, 0.5D, 1.0D, 0.75D),
                        Arguments.of(AttributePolicy.COMBINE, operation, 0.5D, 1.0D, 2.0D),
                        Arguments.of(AttributePolicy.MINIMUM, operation, 0.5D, 1.0D, 0.5D),
                        Arguments.of(AttributePolicy.MAXIMUM, operation, 0.5D, 1.0D, 1.0D));
            }
            return Stream.of(
                    Arguments.of(AttributePolicy.AVERAGE, operation, 2.0D, 4.0D, 3.0D),
                    Arguments.of(AttributePolicy.COMBINE, operation, 2.0D, 4.0D, 6.0D),
                    Arguments.of(AttributePolicy.MINIMUM, operation, 2.0D, 4.0D, 2.0D),
                    Arguments.of(AttributePolicy.MAXIMUM, operation, 2.0D, 4.0D, 4.0D));
        });
    }

    @ParameterizedTest
    @MethodSource("missingSideCases")
    void missingSideUsesTheOperationNeutralContribution(
            AttributePolicy policy,
            AttributeModifier.Operation operation,
            double sourceAmount,
            double expectedAmount,
            boolean omitted) {
        ItemStack first = stack(entry(SHARED_ID, sourceAmount, operation));

        AttributeComposer.Result result = AttributeComposer.compose(first, stack(), rules(policy));

        assertEquals(omitted, result.primary().modifiers().isEmpty());
        if (!omitted) {
            assertEquals(expectedAmount, onlyAmount(result.primary()), 1.0E-12D);
        }
        assertEquals(result.primary(), result.inverse());
    }

    static Stream<Arguments> missingSideCases() {
        return Stream.of(AttributeModifier.Operation.values()).flatMap(operation -> {
            if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                return Stream.of(
                        Arguments.of(AttributePolicy.AVERAGE, operation, 1.0D, 0.5D, false),
                        Arguments.of(AttributePolicy.COMBINE, operation, 1.0D, 1.0D, false),
                        Arguments.of(AttributePolicy.MINIMUM, operation, 1.0D, 0.0D, true),
                        Arguments.of(AttributePolicy.MAXIMUM, operation, 1.0D, 1.0D, false),
                        Arguments.of(AttributePolicy.MINIMUM, operation, -0.5D, -0.5D, false),
                        Arguments.of(AttributePolicy.MAXIMUM, operation, -0.5D, 0.0D, true));
            }
            return Stream.of(
                    Arguments.of(AttributePolicy.AVERAGE, operation, 4.0D, 2.0D, false),
                    Arguments.of(AttributePolicy.COMBINE, operation, 4.0D, 4.0D, false),
                    Arguments.of(AttributePolicy.MINIMUM, operation, 4.0D, 0.0D, true),
                    Arguments.of(AttributePolicy.MAXIMUM, operation, 4.0D, 4.0D, false),
                    Arguments.of(AttributePolicy.MINIMUM, operation, -4.0D, -4.0D, false),
                    Arguments.of(AttributePolicy.MAXIMUM, operation, -4.0D, 0.0D, true));
        });
    }

    @Test
    void handlesNegativeFractionalZeroAndLargeFiniteValues() {
        assertEquals(-0.75D, onlyAmount(compose(
                AttributePolicy.AVERAGE,
                entry(SHARED_ID, -2.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, 0.5D, AttributeModifier.Operation.ADD_VALUE)).primary()));
        assertEquals(0.375D, onlyAmount(compose(
                AttributePolicy.AVERAGE,
                entry(SHARED_ID, 0.25D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                entry(SHARED_ID, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)).primary()));
        assertTrue(compose(
                AttributePolicy.AVERAGE,
                entry(SHARED_ID, -0.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, 0.0D, AttributeModifier.Operation.ADD_VALUE)).primary().modifiers().isEmpty());
        assertEquals(Double.MAX_VALUE, onlyAmount(compose(
                AttributePolicy.AVERAGE,
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_VALUE)).primary()));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    void rejectsNonFiniteInputsWithoutMutatingSources(double invalid) {
        ItemStack first = stack(entry(SHARED_ID, invalid, AttributeModifier.Operation.ADD_VALUE));
        ItemStack second = stack(entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();

        assertThrows(IllegalArgumentException.class,
                () -> AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE)));
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
    }

    static Stream<Double> nonFiniteValues() {
        return Stream.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @Test
    void rejectsNonFiniteArithmeticResults() {
        assertThrows(IllegalArgumentException.class, () -> compose(
                AttributePolicy.COMBINE,
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> compose(
                AttributePolicy.COMBINE,
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                entry(SHARED_ID, Double.MAX_VALUE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
    }

    @Test
    void validatesNonFiniteInputBeforeSeparateOrRejectCanBypassArithmetic() {
        ItemStack invalid = stack(entry(
                SHARED_ID, Double.NaN, AttributeModifier.Operation.ADD_VALUE));
        for (AttributePolicy policy : List.of(AttributePolicy.SEPARATE, AttributePolicy.REJECT)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> AttributeComposer.compose(invalid, stack(), rules(policy)));
            assertTrue(exception.getMessage().contains("Non-finite"));
        }
    }

    @Test
    void rejectsAnAttributeWithoutARegisteredIdentity() {
        Holder<Attribute> unregistered = Holder.direct(
                new RangedAttribute("attribute.test.unregistered", 0.0, -100.0, 100.0));
        ItemStack source = stack(entry(
                unregistered, SHARED_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE,
                EquipmentSlotGroup.FEET, defaultDisplay()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AttributeComposer.compose(source, stack(), rules(AttributePolicy.AVERAGE)));
        assertTrue(exception.getMessage().contains("unregistered attribute"));
    }

    @Test
    void differentModifierIdsRemainDistinctAndKeepTheirIds() {
        Identifier firstId = id("first");
        Identifier secondId = id("second");
        AttributeComposer.Result result = AttributeComposer.compose(
                stack(entry(firstId, 4.0D, AttributeModifier.Operation.ADD_VALUE)),
                stack(entry(secondId, 2.0D, AttributeModifier.Operation.ADD_VALUE)),
                rules(AttributePolicy.AVERAGE));

        assertEquals(List.of(firstId, secondId), modifierIds(result.primary()));
        assertEquals(List.of(2.0D, 1.0D), amounts(result.primary()));
        assertEquals(result.primary(), result.inverse());
    }

    @Test
    void mixedOperationsWithDifferentIdsAreComposedIndependently() {
        Identifier additiveId = id("additive");
        Identifier totalId = id("total");
        ItemStack first = stack(
                entry(additiveId, 2.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(totalId, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ItemStack second = stack(
                entry(additiveId, 4.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(totalId, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE));

        assertEquals(List.of(additiveId, totalId), modifierIds(result.primary()));
        assertEquals(List.of(3.0D, 0.75D), amounts(result.primary()));
        assertEquals(List.of(
                        AttributeModifier.Operation.ADD_VALUE,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                result.primary().modifiers().stream().map(entry -> entry.modifier().operation()).toList());
    }

    @Test
    void registeredModdedAttributeUsesFallbackAndCanBeExplicitlyOverridden() {
        Holder<Attribute> attribute = syntheticAttribute("heat_resistance");
        Identifier attributeId = attribute.unwrapKey().orElseThrow().identifier();
        ItemStack first = stack(entry(
                attribute, SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE,
                EquipmentSlotGroup.FEET, defaultDisplay()));
        ItemStack second = stack(entry(
                attribute, SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE,
                EquipmentSlotGroup.FEET, defaultDisplay()));

        AttributeComposer.Result fallback = AttributeComposer.compose(
                first, second, AttributeRuleDefaults.ruleSet());
        assertEquals(List.of(4.0D), amounts(fallback.primary()));
        assertEquals(List.of(2.0D), amounts(fallback.inverse()));

        AttributeRuleSet override = new AttributeRuleSet(
                AttributePolicy.SEPARATE, Map.of(attributeId, AttributePolicy.AVERAGE));
        AttributeComposer.Result averaged = AttributeComposer.compose(first, second, override);
        assertEquals(3.0D, onlyAmount(averaged.primary()));
        assertEquals(averaged.primary(), averaged.inverse());
    }

    @Test
    void readsTheEffectiveStackComponentInsteadOfCombiningItWithThePrototype() {
        ItemStack first = stack(Items.IRON_BOOTS, ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(SHARED_ID, 10.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build());
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build());
        ItemStack second = stack(entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE));

        assertEquals(3.0D, onlyAmount(result.primary()));
        assertEquals(result.primary(), result.inverse());
    }

    @Test
    void equalSlotAndDisplayMetadataNormalizeForAllSupportedVariants() {
        List<EquipmentSlotGroup> slots = List.of(
                EquipmentSlotGroup.FEET, EquipmentSlotGroup.ARMOR, EquipmentSlotGroup.ANY);
        List<ItemAttributeModifiers.Display> displays = List.of(
                defaultDisplay(),
                ItemAttributeModifiers.Display.hidden(),
                ItemAttributeModifiers.Display.override(Component.literal("custom")));

        for (EquipmentSlotGroup slot : slots) {
            for (ItemAttributeModifiers.Display display : displays) {
                AttributeComposer.Result result = AttributeComposer.compose(
                        stack(entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE, slot, display)),
                        stack(entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE, slot, display)),
                        rules(AttributePolicy.AVERAGE));
                ItemAttributeModifiers.Entry composed = result.primary().modifiers().getFirst();
                assertEquals(3.0D, composed.modifier().amount());
                assertEquals(slot, composed.slot());
                assertEquals(display, composed.display());
                assertEquals(result.primary(), result.inverse());
            }
        }
    }

    @Test
    void operationSlotAndDisplayDifferencesEachCauseOwnerDivergence() {
        assertOwnerDivergence(
                entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        assertOwnerDivergence(
                entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, defaultDisplay()),
                entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.ANY, defaultDisplay()));
        assertOwnerDivergence(
                entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE,
                        EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.hidden()),
                entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE,
                        EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.override(Component.literal("custom"))));
    }

    @Test
    void repeatedSameIdWithinOneSourceCopiesThatIdentityInsteadOfNormalizingIt() {
        ItemStack first = stack(
                entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(SHARED_ID, 6.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack second = stack(entry(SHARED_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE));

        assertEquals(List.of(4.0D, 6.0D), amounts(result.primary()));
        assertEquals(List.of(2.0D), amounts(result.inverse()));
    }

    @Test
    void incompatibleFallbackIsPerIdentity() {
        Identifier incompatibleId = id("a_incompatible");
        Identifier compatibleId = id("b_compatible");
        ItemStack first = stack(
                entry(incompatibleId, 4.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(compatibleId, 8.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack second = stack(
                entry(incompatibleId, 2.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                entry(compatibleId, 4.0D, AttributeModifier.Operation.ADD_VALUE));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE));

        assertEquals(List.of(4.0D, 6.0D), amounts(result.primary()));
        assertEquals(List.of(2.0D, 6.0D), amounts(result.inverse()));
    }

    @Test
    void separateCopiesTheCompleteAttributeToItsOwnerOrientation() {
        Identifier firstId = id("first");
        Identifier secondId = id("second");
        ItemStack first = stack(
                entry(firstId, 0.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(secondId, 4.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack second = stack(entry(firstId, 2.0D, AttributeModifier.Operation.ADD_VALUE));

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.SEPARATE));

        assertEquals(modifiers(first), result.primary());
        assertEquals(modifiers(second), result.inverse());
    }

    @Test
    void rejectInvalidatesAnyRecipeContainingTheAttribute() {
        assertThrows(IllegalArgumentException.class, () -> AttributeComposer.compose(
                stack(entry(SHARED_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE)),
                stack(),
                rules(AttributePolicy.REJECT)));
    }

    @Test
    void outputsAreDeterministicCodecSafeAndSwapAsAnUnorderedPair() {
        Identifier a = id("a");
        Identifier b = id("b");
        ItemStack first = stack(
                entry(b, 4.0D, AttributeModifier.Operation.ADD_VALUE),
                entry(a, 8.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack second = stack(
                entry(a, 2.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                entry(b, 6.0D, AttributeModifier.Operation.ADD_VALUE));
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();
        AttributeRuleSet rules = rules(AttributePolicy.AVERAGE);

        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules);
        AttributeComposer.Result repeated = AttributeComposer.compose(first.copy(), second.copy(), rules);
        AttributeComposer.Result swapped = AttributeComposer.compose(second, first, rules);

        assertEquals(result, repeated);
        assertEquals(result.primary(), swapped.inverse());
        assertEquals(result.inverse(), swapped.primary());
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
        assertTrue(Stream.concat(
                        result.primary().modifiers().stream(), result.inverse().modifiers().stream())
                .noneMatch(entry -> entry.modifier().id().toString().startsWith("mismatchedboots:attribute/")));

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, builtInRegistryAccess());
        JsonElement encoded = ItemAttributeModifiers.CODEC.encodeStart(ops, result.primary()).getOrThrow();
        ItemAttributeModifiers decoded = ItemAttributeModifiers.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals(result.primary(), decoded);
    }

    @Test
    void pairWideConservationAndCombineDuplicationAreExplicit() {
        ItemAttributeModifiers.Entry first = entry(SHARED_ID, 8.0D, AttributeModifier.Operation.ADD_VALUE);
        ItemAttributeModifiers.Entry second = entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_VALUE);

        AttributeComposer.Result average = compose(AttributePolicy.AVERAGE, first, second);
        assertEquals(12.0D, onlyAmount(average.primary()) + onlyAmount(average.inverse()));

        AttributeComposer.Result separate = compose(AttributePolicy.SEPARATE, first, second);
        assertEquals(12.0D, onlyAmount(separate.primary()) + onlyAmount(separate.inverse()));

        AttributeComposer.Result incompatible = compose(
                AttributePolicy.AVERAGE,
                first,
                entry(SHARED_ID, 4.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        assertEquals(12.0D, onlyAmount(incompatible.primary()) + onlyAmount(incompatible.inverse()));

        AttributeComposer.Result combine = compose(AttributePolicy.COMBINE, first, second);
        assertEquals(12.0D, onlyAmount(combine.primary()));
        assertEquals(12.0D, onlyAmount(combine.inverse()));
    }

    private static void assertOwnerDivergence(
            ItemAttributeModifiers.Entry firstEntry, ItemAttributeModifiers.Entry secondEntry) {
        ItemStack first = stack(firstEntry);
        ItemStack second = stack(secondEntry);
        AttributeComposer.Result result = AttributeComposer.compose(first, second, rules(AttributePolicy.AVERAGE));

        assertEquals(modifiers(first), result.primary());
        assertEquals(modifiers(second), result.inverse());
    }

    private static AttributeComposer.Result compose(
            AttributePolicy policy,
            ItemAttributeModifiers.Entry first,
            ItemAttributeModifiers.Entry second) {
        return AttributeComposer.compose(stack(first), stack(second), rules(policy));
    }

    private static AttributeRuleSet rules(AttributePolicy policy) {
        Identifier armorId = Attributes.ARMOR.unwrapKey().orElseThrow().identifier();
        return new AttributeRuleSet(AttributePolicy.SEPARATE, Map.of(armorId, policy));
    }

    private static ItemStack stack(ItemAttributeModifiers.Entry... entries) {
        ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
        Arrays.stream(entries).forEach(entry -> modifiers.add(
                entry.attribute(), entry.modifier(), entry.slot(), entry.display()));
        return stack(Items.IRON_BOOTS, modifiers.build());
    }

    private static ItemStack vanillaBoots(Item item, double armor) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                                Identifier.withDefaultNamespace("armor.boots"),
                                armor,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build();
        return stack(item, modifiers);
    }

    private static ItemStack stack(Item item, ItemAttributeModifiers modifiers) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers)
                .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }

    private static ItemAttributeModifiers.Entry entry(
            Identifier modifierId, double amount, AttributeModifier.Operation operation) {
        return entry(modifierId, amount, operation, EquipmentSlotGroup.FEET, defaultDisplay());
    }

    private static ItemAttributeModifiers.Entry entry(
            Identifier modifierId,
            double amount,
            AttributeModifier.Operation operation,
            EquipmentSlotGroup slot,
            ItemAttributeModifiers.Display display) {
        return entry(Attributes.ARMOR, modifierId, amount, operation, slot, display);
    }

    private static ItemAttributeModifiers.Entry entry(
            Holder<Attribute> attribute,
            Identifier modifierId,
            double amount,
            AttributeModifier.Operation operation,
            EquipmentSlotGroup slot,
            ItemAttributeModifiers.Display display) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(attribute, new AttributeModifier(modifierId, amount, operation), slot, display)
                .build();
        return modifiers.modifiers().getFirst();
    }

    private static ItemAttributeModifiers.Display defaultDisplay() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(id("display_probe"), 1.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build()
                .modifiers()
                .getFirst()
                .display();
    }

    private static ItemAttributeModifiers modifiers(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }

    private static double onlyAmount(ItemAttributeModifiers modifiers) {
        assertEquals(1, modifiers.modifiers().size());
        return modifiers.modifiers().getFirst().modifier().amount();
    }

    private static List<Double> amounts(ItemAttributeModifiers modifiers) {
        return modifiers.modifiers().stream().map(entry -> entry.modifier().amount()).toList();
    }

    private static List<Identifier> modifierIds(ItemAttributeModifiers modifiers) {
        return modifiers.modifiers().stream().map(entry -> entry.modifier().id()).toList();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static Holder<Attribute> syntheticAttribute(String path) {
        MappedRegistry<Attribute> registry = new MappedRegistry<>(Registries.ATTRIBUTE, Lifecycle.stable());
        Holder<Attribute> attribute = registry.register(
                ResourceKey.create(Registries.ATTRIBUTE, id(path)),
                new RangedAttribute("attribute.test." + path, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                RegistrationInfo.BUILT_IN);
        registry.freeze();
        return attribute;
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }
}
