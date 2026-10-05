package games.alejandrocoria.mismatchedboots.composition;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.HashOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeoForgeAttributeCompositionTest {
    private static final Identifier MODIFIER_ID = Identifier.parse("test:source_bonus");
    private static final Identifier REMOVED_ID = Identifier.parse("test:removed_bonus");

    @ParameterizedTest
    @CsvSource({"SEPARATE,1,2", "AVERAGE,1.5,1.5", "COMBINE,3,3", "MINIMUM,1,1", "MAXIMUM,2,2"})
    void eventOnlyModifiersUseConfiguredPoliciesAndDoNotRepeatOnTheRoot(
            AttributePolicy policy, double primaryAmount, double inverseAmount) {
        try (Fixture fixture = new Fixture(policy)) {
            ItemStack first = boots(Items.IRON_BOOTS);
            ItemStack second = boots(Items.DIAMOND_BOOTS);
            ItemStack firstBefore = first.copy();
            ItemStack secondBefore = second.copy();

            var pair = fixture.createPair(first, second);

            assertEquals(primaryAmount, bonus(pair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS)));
            assertEquals(inverseAmount, bonus(pair.inverse().get(DataComponents.ATTRIBUTE_MODIFIERS)));
            assertEquals(primaryAmount, bonus(pair.primary().getAttributeModifiers()));
            assertEquals(inverseAmount, bonus(pair.inverse().getAttributeModifiers()));
            assertEquals(2, fixture.listener.queries);
            assertTrue(ItemStack.matches(firstBefore, first));
            assertTrue(ItemStack.matches(secondBefore, second));
            var primarySources = ModContent.getMixedBootsData(pair.primary()).orElseThrow();
            var inverseSources = ModContent.getMixedBootsData(pair.inverse()).orElseThrow();
            assertTrue(ItemStack.matches(firstBefore, primarySources.left().source()));
            assertTrue(ItemStack.matches(secondBefore, primarySources.right().source()));
            assertTrue(ItemStack.matches(secondBefore, inverseSources.left().source()));
            assertTrue(ItemStack.matches(firstBefore, inverseSources.right().source()));
        }
    }

    @Test
    void sourceQueriesRespectEventReplacementsAndRemovals() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            ItemStack source = boots(Items.IRON_BOOTS);
            source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.LUCK, modifier(MODIFIER_ID, 88), EquipmentSlotGroup.FEET)
                    .add(Attributes.ARMOR, modifier(REMOVED_ID, 8), EquipmentSlotGroup.FEET)
                    .build());
            ItemStack before = source.copy();

            ItemAttributeModifiers effective = Services.PLATFORM.getCompositionAttributeModifiers(source);

            assertEquals(1, effective.modifiers().size());
            assertEquals(1, bonus(effective));
            var entry = effective.modifiers().getFirst();
            assertEquals(Attributes.LUCK, entry.attribute());
            assertEquals(EquipmentSlotGroup.FEET, entry.slot());
            assertEquals(AttributeModifier.Operation.ADD_VALUE, entry.modifier().operation());
            assertEquals(1, fixture.listener.queries);
            assertTrue(ItemStack.matches(before, source));
        }
    }

    @Test
    void emptySourceModifiersRemainEmptyWithoutEventContribution() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            ItemStack source = boots(Items.GOLDEN_BOOTS);
            source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

            ItemAttributeModifiers effective = Services.PLATFORM.getCompositionAttributeModifiers(source);

            assertEquals(ItemAttributeModifiers.EMPTY, effective);
            assertEquals(0, fixture.listener.queries);
            assertEquals(ItemAttributeModifiers.EMPTY, source.get(DataComponents.ATTRIBUTE_MODIFIERS));
        }
    }

    @Test
    void evenAMutatingEventCannotChangeInputsOrStoredSources() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            fixture.listener.mutateSource = true;
            ItemStack first = boots(Items.IRON_BOOTS);
            ItemStack second = boots(Items.DIAMOND_BOOTS);
            ItemStack firstBefore = first.copy();
            ItemStack secondBefore = second.copy();

            var pair = fixture.createPair(first, second);

            assertTrue(ItemStack.matches(firstBefore, first));
            assertTrue(ItemStack.matches(secondBefore, second));
            var sources = ModContent.getMixedBootsData(pair.primary()).orElseThrow();
            assertTrue(ItemStack.matches(firstBefore, sources.left().source()));
            assertTrue(ItemStack.matches(secondBefore, sources.right().source()));
            assertFalse(pair.primary().has(DataComponents.CUSTOM_NAME));
            assertEquals(1, bonus(pair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS)));

            ItemStack recomposed = fixture.recompose(pair.primary(), first, second);

            assertTrue(ItemStack.matches(firstBefore, first));
            assertTrue(ItemStack.matches(secondBefore, second));
            var recomposedSources = ModContent.getMixedBootsData(recomposed).orElseThrow();
            assertTrue(ItemStack.matches(firstBefore, recomposedSources.left().source()));
            assertTrue(ItemStack.matches(secondBefore, recomposedSources.right().source()));
            assertFalse(recomposed.has(DataComponents.CUSTOM_NAME));
            assertEquals(1, bonus(recomposed.get(DataComponents.ATTRIBUTE_MODIFIERS)));
            assertEquals(4, fixture.listener.queries);
        }
    }

    @Test
    void recompositionQueriesBothSourcesAgainButExistingRootsKeepTheirSnapshot() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            ItemStack first = boots(Items.IRON_BOOTS);
            ItemStack second = boots(Items.DIAMOND_BOOTS);
            var pair = fixture.createPair(first, second);
            fixture.listener.firstAmount = 3;
            fixture.listener.secondAmount = 4;

            assertEquals(1, bonus(pair.primary().getAttributeModifiers()));
            assertEquals(2, bonus(pair.inverse().getAttributeModifiers()));
            ItemStack primary = fixture.recompose(pair.primary(), first, second);
            ItemStack inverse = fixture.recompose(pair.inverse(), second, first);

            assertEquals(3, bonus(primary.getAttributeModifiers()));
            assertEquals(4, bonus(inverse.getAttributeModifiers()));
            assertEquals(1, bonus(pair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS)));
            assertEquals(2, bonus(pair.inverse().get(DataComponents.ATTRIBUTE_MODIFIERS)));
            assertEquals(6, fixture.listener.queries);
        }
    }

    @Test
    void rejectPolicyAlsoRejectsAnEventOnlyAttribute() {
        try (Fixture fixture = new Fixture(AttributePolicy.REJECT)) {
            assertThrows(IllegalArgumentException.class, () -> fixture.createPair(
                    boots(Items.IRON_BOOTS), boots(Items.DIAMOND_BOOTS)));
            assertEquals(2, fixture.listener.queries);
        }
    }

    @ParameterizedTest
    @CsvSource({"SEPARATE,1,2", "AVERAGE,1.5,1.5", "COMBINE,3,3", "MINIMUM,1,1", "MAXIMUM,2,2"})
    void wrappedEventAttributesAreCanonicalAndCodecAndHashSafe(
            AttributePolicy policy, double primaryAmount, double inverseAmount) {
        try (Fixture fixture = new Fixture(policy)) {
            fixture.listener.attribute = wrappedHolder(Attributes.LUCK);
            ItemStack first = boots(Items.IRON_BOOTS);
            ItemStack second = boots(Items.DIAMOND_BOOTS);
            ItemAttributeModifiers raw = first.getAttributeModifiers();
            var hashOps = builtInRegistryAccess().createSerializationContext(HashOps.CRC32C_INSTANCE);
            var rawHash = new TypedDataComponent<>(DataComponents.ATTRIBUTE_MODIFIERS, raw).encodeValue(hashOps);
            assertTrue(rawHash.error().orElseThrow().message().contains("Unregistered holder"));

            var pair = fixture.createPair(first, second);

            assertCodecAndHashSafe(pair.primary(), primaryAmount);
            assertCodecAndHashSafe(pair.inverse(), inverseAmount);
            assertCodecAndHashSafe(fixture.recompose(pair.primary(), first, second), primaryAmount);
            assertCodecAndHashSafe(fixture.recompose(pair.inverse(), second, first), inverseAmount);
            assertFalse(first.has(DataComponents.ATTRIBUTE_MODIFIERS));
            assertFalse(second.has(DataComponents.ATTRIBUTE_MODIFIERS));
        }
    }

    @Test
    void normalizesWrappedComponentEntriesWithoutChangingMetadataOrSource() {
        Holder<Attribute> wrapped = wrappedHolder(Attributes.LUCK);
        AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, 0.25,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        ItemAttributeModifiers.Display display = ItemAttributeModifiers.Display.hidden();
        ItemStack source = boots(Items.GOLDEN_BOOTS);
        ItemAttributeModifiers raw = ItemAttributeModifiers.builder()
                .add(wrapped, modifier, EquipmentSlotGroup.ARMOR, display).build();
        source.set(DataComponents.ATTRIBUTE_MODIFIERS, raw);

        ItemAttributeModifiers effective = Services.PLATFORM.getCompositionAttributeModifiers(source);

        assertEquals(List.of(new ItemAttributeModifiers.Entry(
                Attributes.LUCK, modifier, EquipmentSlotGroup.ARMOR, display)), effective.modifiers());
        assertSame(Attributes.LUCK, effective.modifiers().getFirst().attribute());
        assertSame(raw, source.get(DataComponents.ATTRIBUTE_MODIFIERS));
        assertSame(wrapped, raw.modifiers().getFirst().attribute());
    }

    @Test
    void rejectsAnEventAttributeWithoutARegistryKey() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            fixture.listener.attribute = Holder.direct(Attributes.LUCK.value());
            assertThrows(IllegalArgumentException.class,
                    () -> Services.PLATFORM.getCompositionAttributeModifiers(boots(Items.IRON_BOOTS)));
        }
    }

    @Test
    void rejectsAnEventAttributeWithAMissingRegistryKey() {
        try (Fixture fixture = new Fixture(AttributePolicy.SEPARATE)) {
            ResourceKey<Attribute> missing = ResourceKey.create(
                    BuiltInRegistries.ATTRIBUTE.key(), Identifier.parse("test:missing_attribute"));
            fixture.listener.attribute = wrappedHolder(Attributes.LUCK, missing);
            assertThrows(IllegalArgumentException.class,
                    () -> Services.PLATFORM.getCompositionAttributeModifiers(boots(Items.IRON_BOOTS)));
        }
    }

    private static void assertCodecAndHashSafe(ItemStack stack, double expectedAmount) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertSame(Attributes.LUCK, modifiers.modifiers().getFirst().attribute());
        assertEquals(expectedAmount, bonus(modifiers));
        var entry = modifiers.modifiers().getFirst();
        assertEquals(EquipmentSlotGroup.FEET, entry.slot());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, entry.modifier().operation());
        assertEquals(ItemAttributeModifiers.Display.attributeModifiers(), entry.display());
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, builtInRegistryAccess());
        var encoded = ItemAttributeModifiers.CODEC.encodeStart(jsonOps, modifiers).getOrThrow();
        var decoded = ItemAttributeModifiers.CODEC.parse(jsonOps, encoded).getOrThrow();
        assertEquals(modifiers, decoded);
        var hashOps = builtInRegistryAccess().createSerializationContext(HashOps.CRC32C_INSTANCE);
        var hash = new TypedDataComponent<>(DataComponents.ATTRIBUTE_MODIFIERS, modifiers)
                .encodeValue(hashOps).getOrThrow();
        assertEquals(hash, new TypedDataComponent<>(DataComponents.ATTRIBUTE_MODIFIERS, decoded)
                .encodeValue(hashOps).getOrThrow());
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }

    private static Holder<Attribute> wrappedHolder(Holder<Attribute> delegate) {
        return wrappedHolder(delegate, delegate.unwrapKey().orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private static Holder<Attribute> wrappedHolder(Holder<Attribute> delegate, ResourceKey<Attribute> key) {
        // Like Valhelsia's RegistryEntry: reports a key but is not a canonical
        // Holder.Reference and does not expose that reference via getDelegate().
        return (Holder<Attribute>) Proxy.newProxyInstance(Holder.class.getClassLoader(),
                new Class<?>[]{Holder.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getDelegate" -> proxy;
                    case "unwrapKey" -> Optional.of(key);
                    default -> method.invoke(delegate, args);
                });
    }

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, baseComponents()));
    }

    private static DataComponentMap baseComponents() {
        // The loader unit-test bootstrap does not bind data-driven item components.
        return DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build();
    }

    private static double bonus(ItemAttributeModifiers modifiers) {
        var bonuses = modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.LUCK)
                        && entry.modifier().id().equals(MODIFIER_ID))
                .toList();
        assertEquals(1, bonuses.size());
        return bonuses.getFirst().modifier().amount();
    }

    private static AttributeModifier modifier(Identifier id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    public static final class SourceListener {
        int queries;
        double firstAmount = 1;
        double secondAmount = 2;
        boolean mutateSource;
        Holder<Attribute> attribute = Attributes.LUCK;

        @SubscribeEvent
        public void modifyAttributes(ItemAttributeModifierEvent event) {
            ItemStack source = event.getItemStack();
            boolean first = source.is(Items.IRON_BOOTS);
            if (!first && !source.is(Items.DIAMOND_BOOTS)) {
                return;
            }
            queries++;
            event.removeModifier(Attributes.ARMOR, REMOVED_ID);
            event.replaceModifier(attribute,
                    modifier(MODIFIER_ID, first ? firstAmount : secondAmount), EquipmentSlotGroup.FEET);
            if (mutateSource) {
                source.set(DataComponents.CUSTOM_NAME, Component.literal("mutated query copy"));
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final AttributeRuleSet rules;
        private final SourceListener listener = new SourceListener();

        private Fixture(AttributePolicy policy) {
            rules = new AttributeRuleSet(AttributePolicy.SEPARATE,
                    Map.of(Attributes.LUCK.unwrapKey().orElseThrow().identifier(), policy));
            NeoForge.EVENT_BUS.register(listener);
        }

        private MixedBootsPair createPair(ItemStack first, ItemStack second) {
            Item mixedItem = BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
            return MixedBootsFactory.createPair(first, second, Holder.direct(mixedItem, baseComponents()),
                    ModContent.mixedBootsDataType().orElseThrow(), rules, ComponentRuleDefaults.state());
        }

        private ItemStack recompose(ItemStack root, ItemStack first, ItemStack second) {
            return MixedBootsRecomposer.recompose(root, first, second,
                    ModContent.mixedBootsDataType().orElseThrow(), rules, ComponentRuleDefaults.state());
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }
}
