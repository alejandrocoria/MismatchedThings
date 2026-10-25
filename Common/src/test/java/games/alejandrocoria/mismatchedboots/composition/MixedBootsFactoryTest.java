package games.alejandrocoria.mismatchedboots.composition;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsFactoryTest {
    private static DataComponentType<MixedBootsData> mixedDataType;
    private static DataComponentType<String> opaqueType;
    private static DataComponentType<String> rightOnlyType;
    private static DataComponentType<String> addedType;
    private static Holder<Item> outputItem;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
        opaqueType = DataComponentType.<String>builder().persistent(Codec.STRING).build();
        rightOnlyType = DataComponentType.<String>builder().persistent(Codec.STRING).build();
        addedType = DataComponentType.<String>builder().persistent(Codec.STRING).build();
        outputItem = Holder.direct(Items.CHAINMAIL_BOOTS, baseComponents(defaultEquippable()));
    }

    @Test
    void projectsOwnerComponentsAndReplacesStructuralComponents() {
        Equippable ownerEquippable = new Equippable(
                EquipmentSlot.FEET,
                SoundEvents.ARMOR_EQUIP_DIAMOND,
                Optional.of(EquipmentAssets.DIAMOND),
                Optional.of(Identifier.withDefaultNamespace("owner_overlay")),
                Optional.of(HolderSet.direct(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.PLAYER))),
                false,
                false,
                false,
                true,
                true,
                SoundEvents.ARMOR_EQUIP_DIAMOND);
        ItemStack first = boots(Items.IRON_BOOTS, ownerEquippable);
        var customSound = Holder.direct(SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("test", "equip"), 32));
        ItemStack second = boots(Items.GOLDEN_BOOTS, Equippable.builder(EquipmentSlot.FEET).setEquipSound(customSound).build());
        first.set(opaqueType, "alpha");
        second.set(opaqueType, "beta");
        second.set(rightOnlyType, "right-only");
        first.set(DataComponents.CUSTOM_NAME, Component.literal("First owner"));
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Second owner"));
        first.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(Holder.direct(Items.IRON_INGOT))));
        second.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(Holder.direct(Items.GOLD_INGOT))));

        MixedBootsPair pair = create(first, second);
        ItemStack primary = pair.primary();
        ItemStack inverse = pair.inverse();

        assertEquals(Items.CHAINMAIL_BOOTS, primary.getItem());
        assertEquals(1, primary.getCount());
        assertEquals(1, primary.get(DataComponents.MAX_STACK_SIZE));
        assertEquals(Component.translatable("item.mismatchedboots.mixed_boots"), primary.get(DataComponents.ITEM_NAME));
        assertEquals(ModContent.MIXED_BOOTS_ID, primary.get(DataComponents.ITEM_MODEL));
        assertRepairableAccepts(primary, Items.IRON_INGOT, Items.GOLD_INGOT);
        assertRepairableAccepts(inverse, Items.IRON_INGOT, Items.GOLD_INGOT);
        assertEquals("alpha", primary.get(opaqueType));
        assertEquals("beta", inverse.get(opaqueType));
        assertNull(primary.get(rightOnlyType));
        assertEquals("right-only", inverse.get(rightOnlyType));
        assertEquals(Component.literal("First owner"), primary.get(DataComponents.CUSTOM_NAME));
        assertEquals(Component.literal("Second owner"), inverse.get(DataComponents.CUSTOM_NAME));

        Equippable projected = primary.get(DataComponents.EQUIPPABLE);
        assertEquals(EquipmentSlot.FEET, projected.slot());
        assertEquals(customSound, projected.equipSound());
        assertEquals(ownerEquippable.dispensable(), projected.dispensable());
        assertEquals(ownerEquippable.swappable(), projected.swappable());
        assertEquals(ownerEquippable.damageOnHurt(), projected.damageOnHurt());
        assertEquals(ownerEquippable.equipOnInteract(), projected.equipOnInteract());
        assertEquals(ownerEquippable.canBeSheared(), projected.canBeSheared());
        assertEquals(ownerEquippable.shearingSound(), projected.shearingSound());
        assertEquals(ownerEquippable.allowedEntities(), projected.allowedEntities());
        assertEquals(ownerEquippable.assetId(), projected.assetId());
        assertTrue(projected.cameraOverlay().isEmpty());

        MixedBootsData data = primary.get(mixedDataType);
        assertEquals(Items.IRON_BOOTS, data.left().source().getItem());
        assertEquals("alpha", data.left().source().get(opaqueType));
        assertEquals(Items.GOLDEN_BOOTS, data.right().source().getItem());
        assertEquals("beta", data.right().source().get(opaqueType));
    }

    @Test
    void selectsOnlyCustomEquipSoundForBothOutputsOtherwiseKeepsOwner() {
        var customA = Holder.direct(SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("test", "equip_a"), 32));
        var customB = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("other", "equip_b")));
        var sameIdDifferentRange = Holder.direct(SoundEvent.createFixedRangeEvent(customA.value().location(), 64));
        var inlineVanilla = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("item.armor.equip_iron")));
        record Case(Holder<SoundEvent> left, Holder<SoundEvent> right, Holder<SoundEvent> primary, Holder<SoundEvent> inverse) {
        }
        for (var example : List.of(
                new Case(SoundEvents.ARMOR_EQUIP_IRON, SoundEvents.ARMOR_EQUIP_DIAMOND,
                         SoundEvents.ARMOR_EQUIP_IRON, SoundEvents.ARMOR_EQUIP_DIAMOND),
                new Case(customA, SoundEvents.ARMOR_EQUIP_DIAMOND, customA, customA),
                new Case(inlineVanilla, customB, customB, customB),
                new Case(customA, customB, customA, customB),
                new Case(customA, customA, customA, customA),
                new Case(customA, sameIdDifferentRange, customA, sameIdDifferentRange))) {
            ItemStack left = boots(Items.IRON_BOOTS, Equippable.builder(EquipmentSlot.FEET).setEquipSound(example.left()).build());
            ItemStack right = boots(Items.DIAMOND_BOOTS, Equippable.builder(EquipmentSlot.FEET).setEquipSound(example.right()).build());
            ItemStack leftBefore = left.copy(), rightBefore = right.copy();
            var pair = create(left, right);
            assertEquals(example.primary(), pair.primary().get(DataComponents.EQUIPPABLE).equipSound());
            assertEquals(example.inverse(), pair.inverse().get(DataComponents.EQUIPPABLE).equipSound());
            assertTrue(ItemStack.matches(leftBefore, left));
            assertTrue(ItemStack.matches(rightBefore, right));
            assertTrue(ItemStack.matches(leftBefore, pair.primary().get(mixedDataType).left().source()));
            assertTrue(ItemStack.matches(rightBefore, pair.inverse().get(mixedDataType).left().source()));
        }
    }

    @Test
    void recompositionSelectsSoundFromCurrentSourcesWithoutMutatingRootOrHistory() {
        var custom = Holder.direct(SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath("test", "equip"), 32));
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.DIAMOND_BOOTS);
        var initial = create(left, right);
        for (boolean inverse : List.of(false, true)) {
            ItemStack root = inverse ? initial.inverse() : initial.primary();
            ItemStack rootBefore = root.copy();
            ItemStack changedLeft = (inverse ? right : left).copy();
            ItemStack changedRight = (inverse ? left : right).copy();
            changedRight.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setEquipSound(custom).build());
            ItemStack changedBefore = changedRight.copy();
            ItemStack result = MixedBootsRecomposer.recompose(root, changedLeft, changedRight,
                                                              mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
            assertEquals(custom, result.get(DataComponents.EQUIPPABLE).equipSound());
            assertTrue(ItemStack.matches(rootBefore, root));
            assertTrue(ItemStack.matches(changedBefore, changedRight));
            assertTrue(ItemStack.matches(changedBefore, result.get(mixedDataType).right().source()));
            // Removing a custom sound or changing it to a second custom must use the current sources, not the old root.
            ItemStack reset = MixedBootsRecomposer.recompose(result, changedLeft, inverse ? left : right,
                                                             mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
            assertEquals(changedLeft.get(DataComponents.EQUIPPABLE).equipSound(), reset.get(DataComponents.EQUIPPABLE).equipSound());
            changedLeft.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                                                                 .setEquipSound(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(
                                                                         "other",
                                                                         "equip")))).build());
            ItemStack twoCustom = MixedBootsRecomposer.recompose(result, changedLeft, changedRight,
                                                                 mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
            assertEquals(changedLeft.get(DataComponents.EQUIPPABLE).equipSound(), twoCustom.get(DataComponents.EQUIPPABLE).equipSound());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "100,0,100,0,100,0",
            "101,0,100,0,100,0",
            "101,50,101,51,101,51",
            "0,0,100,60,50,30",
            "0,0,0,0,0,0",
            "100,100,100,100,100,100"
    })
    void derivesConservativeDurability(
            int firstMaximum,
            int firstDamage,
            int secondMaximum,
            int secondDamage,
            int expectedMaximum,
            int expectedDamage) {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, firstMaximum, firstDamage);
        setDurability(second, secondMaximum, secondDamage);

        MixedBootsPair pair = create(first, second);

        assertEquals(expectedMaximum, pair.primary().getOrDefault(DataComponents.MAX_DAMAGE, 0));
        assertEquals(expectedDamage, pair.primary().getOrDefault(DataComponents.DAMAGE, 0));
        assertEquals(expectedMaximum, pair.inverse().getOrDefault(DataComponents.MAX_DAMAGE, 0));
        assertEquals(expectedDamage, pair.inverse().getOrDefault(DataComponents.DAMAGE, 0));
    }

    @Test
    void durabilityPropertiesHoldAcrossOddAndMalformedValues() {
        for (int firstMaximum = 0; firstMaximum <= 12; firstMaximum++) {
            for (int secondMaximum = 0; secondMaximum <= 12; secondMaximum++) {
                for (int firstDamage = 0; firstDamage <= firstMaximum + 2; firstDamage++) {
                    for (int secondDamage = 0; secondDamage <= secondMaximum + 2; secondDamage++) {
                        ItemStack first = boots(Items.IRON_BOOTS);
                        ItemStack second = boots(Items.GOLDEN_BOOTS);
                        setDurability(first, firstMaximum, firstDamage);
                        setDurability(second, secondMaximum, secondDamage);

                        DurabilityMerger.Result result = DurabilityMerger.merge(
                                first, second, DurabilityPolicy.AVERAGE).primary();
                        long sourceRemaining = remaining(firstMaximum, firstDamage) + remaining(secondMaximum, secondDamage);
                        long resultRemaining = (long) result.maximum() - result.damage();

                        assertTrue(2L * result.maximum() <= (long) firstMaximum + secondMaximum);
                        assertTrue(2L * resultRemaining <= sourceRemaining);
                        assertTrue((long) firstMaximum + secondMaximum - 2L * result.maximum() <= 1L);
                        assertTrue(sourceRemaining - 2L * resultRemaining <= 1L);
                        assertTrue(result.damage() >= 0 && result.damage() <= result.maximum());
                    }
                }
            }
        }
    }

    @Test
    void oneSidedUnbreakableIsOmittedWithoutChangingDerivedDurability() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, 100, 10);
        setDurability(second, 200, 20);
        first.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);

        MixedBootsPair pair = create(first, second);

        assertFalse(pair.primary().has(DataComponents.UNBREAKABLE));
        assertFalse(pair.inverse().has(DataComponents.UNBREAKABLE));
        assertEquals(150, pair.primary().getMaxDamage());
        assertEquals(pair.primary().getMaxDamage(), pair.inverse().getMaxDamage());
        assertEquals(pair.primary().getDamageValue(), pair.inverse().getDamageValue());
    }

    @Test
    void separateDurabilityFollowsEachOwnerAndClampsSourceDamage() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, 101, 150);
        DurabilityMerger.Results clamped = DurabilityMerger.merge(first, second, DurabilityPolicy.SEPARATE);
        assertEquals(new DurabilityMerger.Result(101, 101), clamped.primary());
        assertEquals(new DurabilityMerger.Result(0, 0), clamped.inverse());

        first.set(DataComponents.DAMAGE, 100);
        ComponentRules rules = new ComponentRules(
                DurabilityPolicy.SEPARATE, RepairCostPolicy.MAXIMUM, ComponentRuleDefaults.ruleSet());

        MixedBootsPair pair = MixedBootsFactory.createPair(
                first, second, outputItem, mixedDataType, AttributeRuleDefaults.ruleSet(), rules);

        assertEquals(101, pair.primary().getMaxDamage());
        assertEquals(100, pair.primary().getDamageValue());
        assertFalse(pair.inverse().has(DataComponents.MAX_DAMAGE));
        assertFalse(pair.inverse().has(DataComponents.DAMAGE));
        assertEquals(100, pair.primary().get(mixedDataType).left().source().getDamageValue());
    }

    @Test
    void repairCostPoliciesHandleOrientationAbsenceRoundingAndBoundaries() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        first.set(DataComponents.REPAIR_COST, 2);
        second.set(DataComponents.REPAIR_COST, 5);

        assertEquals(new RepairCostMerger.Results(2, 5),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.OWNER));
        assertEquals(new RepairCostMerger.Results(4, 4),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.AVERAGE));
        assertEquals(new RepairCostMerger.Results(2, 2),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.MINIMUM));
        assertEquals(new RepairCostMerger.Results(5, 5),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.MAXIMUM));

        first.remove(DataComponents.REPAIR_COST);
        assertEquals(new RepairCostMerger.Results(0, 5),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.OWNER));
        assertEquals(new RepairCostMerger.Results(3, 3),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.AVERAGE));
        assertEquals(new RepairCostMerger.Results(0, 0),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.MINIMUM));

        first.set(DataComponents.REPAIR_COST, Integer.MAX_VALUE);
        second.set(DataComponents.REPAIR_COST, Integer.MAX_VALUE);
        assertEquals(new RepairCostMerger.Results(Integer.MAX_VALUE, Integer.MAX_VALUE),
                     RepairCostMerger.merge(first, second, RepairCostPolicy.AVERAGE));

        first.set(DataComponents.REPAIR_COST, -1);
        second.set(DataComponents.REPAIR_COST, 5);
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();
        assertThrows(IllegalArgumentException.class,
                     () -> RepairCostMerger.merge(first, second, RepairCostPolicy.MAXIMUM));
        assertThrows(IllegalArgumentException.class, () -> create(first, second));
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
    }

    @Test
    void initialRepairCostUsesMaximumAndOmitsZero() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        first.set(DataComponents.REPAIR_COST, 3);
        second.set(DataComponents.REPAIR_COST, 8);

        MixedBootsPair pair = create(first, second);

        assertEquals(8, pair.primary().get(DataComponents.REPAIR_COST));
        assertEquals(8, pair.inverse().get(DataComponents.REPAIR_COST));
        pair = create(boots(Items.IRON_BOOTS), boots(Items.GOLDEN_BOOTS));
        assertFalse(pair.primary().has(DataComponents.REPAIR_COST));
        assertFalse(pair.inverse().has(DataComponents.REPAIR_COST));
    }

    @Test
    void composesAttributesByIdentityWithoutGeneratingModifierIds() {
        Identifier sharedId = Identifier.fromNamespaceAndPath("test", "shared");
        ItemAttributeModifiers.Display customDisplay = ItemAttributeModifiers.Display.override(Component.literal("custom"));
        Holder<Attribute> moddedAttribute = syntheticAttribute("mana");
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.IRON_BOOTS);
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(Attributes.ARMOR,
                                                                                 modifier(sharedId, 4.0, AttributeModifier.Operation.ADD_VALUE),
                                                                                 EquipmentSlotGroup.FEET)
                                                                            .add(Attributes.MOVEMENT_SPEED,
                                                                                 modifier(sharedId,
                                                                                          -0.2,
                                                                                          AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                                                                 EquipmentSlotGroup.ANY,
                                                                                 ItemAttributeModifiers.Display.hidden())
                                                                            .add(moddedAttribute,
                                                                                 modifier(Identifier.fromNamespaceAndPath("test", "mana"),
                                                                                          3.5,
                                                                                          AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                                                                                 EquipmentSlotGroup.ARMOR,
                                                                                 customDisplay)
                                                                            .build());
        second.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                             .add(Attributes.ARMOR,
                                                                                  modifier(sharedId, 2.0, AttributeModifier.Operation.ADD_VALUE),
                                                                                  EquipmentSlotGroup.FEET)
                                                                             .add(Attributes.ARMOR_TOUGHNESS,
                                                                                  modifier(Identifier.fromNamespaceAndPath("test", "zero"),
                                                                                           0.0,
                                                                                           AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                                                                                  EquipmentSlotGroup.ARMOR)
                                                                             .build());

        MixedBootsPair firstPair = create(first, second);
        MixedBootsPair repeatedPair = create(first.copy(), second.copy());
        ItemAttributeModifiers primary = firstPair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers repeated = repeatedPair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS);

        ItemAttributeModifiers inverse = firstPair.inverse().get(DataComponents.ATTRIBUTE_MODIFIERS);

        assertEquals(3, primary.modifiers().size());
        assertEquals(2, inverse.modifiers().size());
        assertEquals(List.of(-0.1, 3.0, 3.5),
                     primary.modifiers().stream().map(entry -> entry.modifier().amount()).sorted().toList());
        assertEquals(List.of(-0.1, 3.0),
                     inverse.modifiers().stream().map(entry -> entry.modifier().amount()).sorted().toList());
        assertTrue(primary.modifiers().stream().anyMatch(entry ->
                                                                 entry.slot() == EquipmentSlotGroup.ANY
                                                                         && entry.display().equals(ItemAttributeModifiers.Display.hidden())));
        assertTrue(primary.modifiers().stream().anyMatch(entry -> entry.display().equals(customDisplay)));
        assertTrue(inverse.modifiers().stream().noneMatch(entry -> entry.display().equals(customDisplay)));
        assertEquals(primary, repeated);
        assertEquals(List.of(sharedId, Identifier.fromNamespaceAndPath("test", "mana"), sharedId),
                     primary.modifiers().stream().map(entry -> entry.modifier().id()).toList());
        assertTrue(primary.modifiers().stream().noneMatch(entry ->
                                                                  entry.modifier().id().toString().startsWith("mismatchedboots:attribute/")));
        assertTrue(inverse.modifiers().stream().noneMatch(entry ->
                                                                  entry.modifier().id().toString().startsWith("mismatchedboots:attribute/")));
    }

    @Test
    void factoryUsesTheExplicitAttributeRuleSnapshot() {
        AttributeRuleSet combineArmor = new AttributeRuleSet(
                AttributePolicy.SEPARATE,
                Map.of(Identifier.withDefaultNamespace("armor"), AttributePolicy.COMBINE));
        // Explicit composition must not accidentally read the unrelated client snapshot.
        AttributeRuleState.install(new AttributeRuleSet(AttributePolicy.REJECT, Map.of()));
        try {
            ItemStack iron = boots(Items.IRON_BOOTS);
            iron.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                               .add(Attributes.ARMOR,
                                                                                    modifier(Identifier.withDefaultNamespace("armor.boots"), 2.0D,
                                                                                             AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET)
                                                                               .build());
            ItemStack gold = boots(Items.GOLDEN_BOOTS);
            gold.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                               .add(Attributes.ARMOR,
                                                                                    modifier(Identifier.withDefaultNamespace("armor.boots"), 1.0D,
                                                                                             AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET)
                                                                               .build());
            MixedBootsPair pair = MixedBootsFactory.createPair(
                    iron, gold, outputItem, mixedDataType, combineArmor, ComponentRuleDefaults.state());

            for (ItemStack output : List.of(pair.primary(), pair.inverse())) {
                List<ItemAttributeModifiers.Entry> armor = output.getOrDefault(
                                                                         DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                                                                 .modifiers().stream()
                                                                 .filter(entry -> entry.attribute().equals(Attributes.ARMOR))
                                                                 .toList();
                assertEquals(1, armor.size());
                assertEquals(Identifier.withDefaultNamespace("armor.boots"), armor.getFirst().modifier().id());
                assertEquals(3.0D, armor.getFirst().modifier().amount());
            }
        } finally {
            AttributeRuleState.restoreDefaults();
        }
    }

    @Test
    void factoryUsesTheExplicitCompleteComponentRuleSnapshot() {
        ComponentRules rules = new ComponentRules(
                DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER, ComponentRuleDefaults.ruleSet());
        ComponentRuleState.install(new ComponentRules(
                DurabilityPolicy.AVERAGE, RepairCostPolicy.MAXIMUM, ComponentRuleDefaults.ruleSet()));
        try {
            ItemStack first = boots(Items.IRON_BOOTS);
            ItemStack second = boots(Items.GOLDEN_BOOTS);
            setDurability(first, 100, 10);
            setDurability(second, 200, 40);
            first.set(DataComponents.REPAIR_COST, 2);
            second.set(DataComponents.REPAIR_COST, 5);

            MixedBootsPair pair = MixedBootsFactory.createPair(first, second, outputItem, mixedDataType,
                                                               AttributeRuleDefaults.ruleSet(), rules);

            assertEquals(100, pair.primary().getMaxDamage());
            assertEquals(10, pair.primary().getDamageValue());
            assertEquals(2, pair.primary().get(DataComponents.REPAIR_COST));
            assertEquals(200, pair.inverse().getMaxDamage());
            assertEquals(40, pair.inverse().getDamageValue());
            assertEquals(5, pair.inverse().get(DataComponents.REPAIR_COST));
        } finally {
            ComponentRuleState.restoreDefaults();
        }
    }

    @Test
    void rejectsNonFiniteAttributeAmountsWithoutMutatingInputs() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(
                                                                                    Attributes.ARMOR,
                                                                                    modifier(Identifier.fromNamespaceAndPath("test", "invalid"),
                                                                                             Double.NaN,
                                                                                             AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET)
                                                                            .build());
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();

        assertThrows(IllegalArgumentException.class, () -> create(first, second));
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
    }

    @Test
    void attributeOperationOrderIsIndependentOfOrientation() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(
                                                                                    Attributes.ARMOR,
                                                                                    modifier(Identifier.fromNamespaceAndPath("test", "add"),
                                                                                             10.0,
                                                                                             AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET)
                                                                            .build());
        second.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                             .add(
                                                                                     Attributes.ARMOR,
                                                                                     modifier(Identifier.fromNamespaceAndPath("test", "total"),
                                                                                              1.0,
                                                                                              AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                                                                                     EquipmentSlotGroup.FEET)
                                                                             .build());

        MixedBootsPair pair = create(first, second);
        ItemAttributeModifiers primary = pair.primary().get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers inverse = pair.inverse().get(DataComponents.ATTRIBUTE_MODIFIERS);

        assertEquals(
                primary.compute(Attributes.ARMOR, 10.0, EquipmentSlot.FEET),
                inverse.compute(Attributes.ARMOR, 10.0, EquipmentSlot.FEET));
    }

    @Test
    void attributeIdsAndMetadataSurviveCodecRoundTrip() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(
                                                                                    Attributes.ARMOR,
                                                                                    modifier(Identifier.fromNamespaceAndPath("test", "large"),
                                                                                             Double.MAX_VALUE,
                                                                                             AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET,
                                                                                    ItemAttributeModifiers.Display.override(Component.literal("large")))
                                                                            .build());
        ItemAttributeModifiers original = create(first, second).primary().get(DataComponents.ATTRIBUTE_MODIFIERS);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, builtInRegistryAccess());

        JsonElement encoded = ItemAttributeModifiers.CODEC.encodeStart(ops, original).getOrThrow();
        ItemAttributeModifiers decoded = ItemAttributeModifiers.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(original, decoded);
        assertTrue(Double.isFinite(decoded.modifiers().getFirst().modifier().amount()));
    }

    @ParameterizedTest
    @CsvSource({
            "0,3,4,3",
            "2,3,4,3",
            "3,3,4,4",
            "4,4,4,5",
            "5,5,4,5",
            "1,1,1,2"
    })
    void mergesEnchantmentLevels(int firstLevel, int secondLevel, int declaredMaximum, int expected) {
        Holder<Enchantment> enchantment = syntheticEnchantment("level_case", declaredMaximum, HolderSet.empty());
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setEnchantment(first, enchantment, firstLevel);
        setEnchantment(second, enchantment, secondLevel);

        ItemEnchantments merged = EnchantmentMerger.merge(first, second);

        assertEquals(expected, merged.getLevel(enchantment));
    }

    @Test
    void keepsIncompatibleEnchantmentsAndDuplicatesTheUnionToBothOutputs() {
        MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        Holder.Reference<Enchantment> frost = registerEnchantment(registry, "frost", 2, HolderSet.empty());
        Holder.Reference<Enchantment> depth = registerEnchantment(registry, "depth", 3, HolderSet.direct(frost));
        registry.freeze();
        assertFalse(Enchantment.areCompatible(depth, frost));

        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setEnchantment(first, depth, 3);
        setEnchantment(second, frost, 2);

        MixedBootsPair pair = create(first, second);
        ItemEnchantments primary = pair.primary().get(DataComponents.ENCHANTMENTS);
        ItemEnchantments inverse = pair.inverse().get(DataComponents.ENCHANTMENTS);

        assertEquals(3, primary.getLevel(depth));
        assertEquals(2, primary.getLevel(frost));
        assertEquals(primary, inverse);
    }

    @Test
    void outputAndSourceMutationNeverRewriteSnapshotsOrInputs() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, 100, 10);
        setDurability(second, 200, 20);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();

        MixedBootsPair pair = create(first, second);
        first.setDamageValue(99);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("caller mutation"));
        pair.primary().setDamageValue(50);
        pair.primary().set(DataComponents.CUSTOM_NAME, Component.literal("root mutation"));
        MixedBootsData snapshots = pair.primary().get(mixedDataType);

        assertEquals(10, snapshots.left().source().getDamageValue());
        assertEquals(Component.literal("original"), snapshots.left().source().get(DataComponents.CUSTOM_NAME));
        assertEquals(20, snapshots.right().source().getDamageValue());
        assertTrue(ItemStack.matches(firstBefore, snapshots.left().source()));
        assertTrue(ItemStack.matches(secondBefore, snapshots.right().source()));
        assertTrue(ItemStack.matches(secondBefore, second));
    }

    @Test
    void repeatedConstructionIsDeterministicAndInvalidInputReturnsNothing() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, 101, 50);
        setDurability(second, 203, 17);

        MixedBootsPair firstResult = create(first, second);
        MixedBootsPair secondResult = create(first.copy(), second.copy());

        assertTrue(ItemStack.matches(firstResult.primary(), secondResult.primary()));
        assertTrue(ItemStack.matches(firstResult.inverse(), secondResult.inverse()));

        ItemStack invalid = boots(Items.IRON_BOOTS);
        invalid.setCount(2);
        assertThrows(IllegalArgumentException.class, () -> create(invalid, second));
    }

    @Test
    void recompositionProjectsUnknownOwnerChangesAndPreservesLiveRootValues() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        setDurability(left, 100, 0);
        setDurability(right, 100, 0);
        left.set(opaqueType, "old");
        left.set(rightOnlyType, "removed");
        right.set(DataComponents.REPAIRABLE,
                  new Repairable(HolderSet.direct(Holder.direct(Items.GOLD_INGOT))));
        ItemStack root = create(left, right).primary();
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Anvil name"));
        root.set(DataComponents.REPAIR_COST, 13);
        root.setDamageValue(17);
        ItemStack replacement = left.copy();
        replacement.set(opaqueType, "new");
        replacement.remove(rightOnlyType);
        replacement.set(addedType, "introduced");
        replacement.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                                  .add(Attributes.ARMOR,
                                                                                       modifier(Identifier.fromNamespaceAndPath("test",
                                                                                                                                "smithing_armor"),
                                                                                                4.0,
                                                                                                AttributeModifier.Operation.ADD_VALUE),
                                                                                       EquipmentSlotGroup.FEET)
                                                                                  .build());
        replacement.set(DataComponents.EQUIPPABLE,
                        Equippable.builder(EquipmentSlot.FEET).setAsset(EquipmentAssets.DIAMOND).build());
        replacement.set(DataComponents.REPAIRABLE,
                        new Repairable(HolderSet.direct(Holder.direct(Items.DIAMOND))));
        ItemStack rootBefore = root.copy();
        ItemStack replacementBefore = replacement.copy();

        ItemStack result = MixedBootsRecomposer.recompose(
                root, replacement, right, mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());

        assertEquals("new", result.get(opaqueType));
        assertNull(result.get(rightOnlyType));
        assertEquals("introduced", result.get(addedType));
        assertEquals(Component.literal("Anvil name"), result.get(DataComponents.CUSTOM_NAME));
        assertEquals(13, result.get(DataComponents.REPAIR_COST));
        assertEquals(17, result.getDamageValue());
        assertRepairableAccepts(result, Items.DIAMOND, Items.GOLD_INGOT);
        assertEquals(AttributeComposer.compose(replacement, right, AttributeRuleDefaults.ruleSet()).primary(),
                     result.get(DataComponents.ATTRIBUTE_MODIFIERS));
        assertEquals(Optional.of(EquipmentAssets.DIAMOND), result.get(DataComponents.EQUIPPABLE).assetId());
        assertEquals("new", result.get(mixedDataType).left().source().get(opaqueType));
        assertTrue(ItemStack.matches(rootBefore, root));
        assertTrue(ItemStack.matches(replacementBefore, replacement));
    }

    @Test
    void recompositionKeepsDivergedUnknownRootAndClampsRawDamage() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        setDurability(left, 100, 0);
        setDurability(right, 100, 0);
        left.set(opaqueType, "source");
        ItemStack root = create(left, right).primary();
        root.set(opaqueType, "root override");
        root.setDamageValue(90);
        ItemStack replacement = left.copy();
        replacement.set(opaqueType, "new source");
        setDurability(replacement, 20, 0);
        ItemStack newRight = right.copy();
        setDurability(newRight, 20, 0);

        ItemStack result = MixedBootsRecomposer.recompose(
                root, replacement, newRight, mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());

        assertEquals("root override", result.get(opaqueType));
        assertEquals("new source", result.get(mixedDataType).left().source().get(opaqueType));
        assertEquals(20, result.getMaxDamage());
        assertEquals(20, result.getDamageValue());
    }

    @Test
    void recompositionUsesActiveDurabilityPolicyAndPreservesLiveRepairCost() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        setDurability(left, 100, 10);
        setDurability(right, 200, 20);
        left.set(DataComponents.REPAIR_COST, 2);
        right.set(DataComponents.REPAIR_COST, 8);
        ItemStack root = create(left, right).primary();
        root.setDamageValue(90);
        root.set(DataComponents.REPAIR_COST, 17);
        ItemStack newLeft = left.copy();
        setDurability(newLeft, 20, 0);
        ComponentRules changedRules = new ComponentRules(
                DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER, ComponentRuleDefaults.ruleSet());

        ItemStack result = MixedBootsRecomposer.recompose(
                root, newLeft, right, mixedDataType, AttributeRuleDefaults.ruleSet(), changedRules);

        assertEquals(20, result.getMaxDamage());
        assertEquals(20, result.getDamageValue());
        assertEquals(17, result.get(DataComponents.REPAIR_COST));
        assertTrue(ItemStack.matches(root.get(mixedDataType).right().source(),
                                     result.get(mixedDataType).right().source()));

        root.remove(DataComponents.REPAIR_COST);
        result = MixedBootsRecomposer.recompose(
                root, newLeft, right, mixedDataType, AttributeRuleDefaults.ruleSet(), changedRules);
        assertFalse(result.has(DataComponents.REPAIR_COST));
    }

    @Test
    void projectsDefaultsOnBothRootsWithoutChangingSnapshots() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        ItemLore lore = new ItemLore(List.of(Component.literal("Source lore")));
        left.set(DataComponents.LORE, lore);
        left.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        left.set(DataComponents.ENCHANTABLE, new Enchantable(25));
        right.set(DataComponents.ENCHANTABLE, new Enchantable(9));
        ItemStack before = left.copy();
        MixedBootsPair pair = create(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            assertNull(root.get(DataComponents.LORE));
            assertNull(root.get(DataComponents.UNBREAKABLE));
            assertEquals(new Enchantable(9), root.get(DataComponents.ENCHANTABLE));
        }
        assertEquals(lore, pair.primary().get(mixedDataType).left().source().get(DataComponents.LORE));
        assertTrue(ItemStack.matches(before, left));
    }

    @ParameterizedTest
    @CsvSource({"1, 0, 1", "1, 1, 1", "1, 2, 2"})
    void defaultStepHeightKeepsTheStrongerContributionInBothOrientations(
            double leftAmount, double rightAmount, double expected) {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        Identifier modifierId = Identifier.parse("test:step_assist");
        for (ItemStack source : List.of(left, right)) {
            double amount = source == left ? leftAmount : rightAmount;
            if (amount != 0) {
                source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                                     .add(Attributes.STEP_HEIGHT,
                                                                                          new AttributeModifier(modifierId,
                                                                                                                amount,
                                                                                                                AttributeModifier.Operation.ADD_VALUE),
                                                                                          EquipmentSlotGroup.FEET)
                                                                                     .build());
            }
        }
        MixedBootsPair pair = create(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            var entries = root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
            assertEquals(1, entries.size());
            assertEquals(Attributes.STEP_HEIGHT, entries.getFirst().attribute());
            assertEquals(modifierId, entries.getFirst().modifier().id());
            assertEquals(expected, entries.getFirst().modifier().amount());
        }
    }

    @Test
    void recomposesSymmetricValuesAndPreservesRootAdditionsAndRemovals() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        left.set(DataComponents.ENCHANTABLE, new Enchantable(25));
        right.set(DataComponents.ENCHANTABLE, new Enchantable(9));
        MixedBootsPair pair = create(left, right);
        ItemStack changedRight = right.copy();
        changedRight.set(DataComponents.ENCHANTABLE, new Enchantable(15));
        for (boolean inverse : new boolean[]{false, true}) {
            ItemStack root = inverse ? pair.inverse() : pair.primary();
            ItemStack newLeft = inverse ? changedRight : left;
            ItemStack newRight = inverse ? left : changedRight;
            ItemStack result = MixedBootsRecomposer.recompose(root, newLeft, newRight,
                                                              mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
            assertEquals(new Enchantable(15), result.get(DataComponents.ENCHANTABLE));
            root.remove(DataComponents.ENCHANTABLE);
            ItemLore liveLore = new ItemLore(List.of(Component.literal("Live lore")));
            root.set(DataComponents.LORE, liveLore);
            result = MixedBootsRecomposer.recompose(root, newLeft, newRight,
                                                    mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
            assertNull(result.get(DataComponents.ENCHANTABLE));
            assertEquals(liveLore, result.get(DataComponents.LORE));
        }
        ItemStack root = create(left, right).primary();
        changedRight.remove(DataComponents.ENCHANTABLE);
        assertNull(MixedBootsRecomposer.recompose(root, left, changedRight,
                                                  mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state())
                                       .get(DataComponents.ENCHANTABLE));
    }

    @Test
    void changedRulesUseCurrentProjectionWithoutPretendingToKnowProvenance() {
        ItemStack left = boots(Items.IRON_BOOTS);
        ItemStack right = boots(Items.GOLDEN_BOOTS);
        left.set(DataComponents.ENCHANTABLE, new Enchantable(25));
        right.set(DataComponents.ENCHANTABLE, new Enchantable(9));
        ItemStack root = create(left, right).primary();
        var entries = new java.util.LinkedHashMap<>(ComponentRuleDefaults.rules());
        entries.put(Identifier.withDefaultNamespace("enchantable"), ComponentRule.of(ComponentPolicy.MAXIMUM));
        ComponentRuleSet changedRules = new ComponentRuleSet(entries);
        ItemStack upgraded = left.copy();
        upgraded.set(DataComponents.ENCHANTABLE, new Enchantable(30));
        assertEquals(new Enchantable(9), MixedBootsRecomposer.recompose(root, upgraded, right,
                                                                        mixedDataType, AttributeRuleDefaults.ruleSet(), withComponents(changedRules))
                                                             .get(DataComponents.ENCHANTABLE));
        root.set(DataComponents.ENCHANTABLE, new Enchantable(25));
        assertEquals(new Enchantable(30), MixedBootsRecomposer.recompose(root, upgraded, right,
                                                                         mixedDataType, AttributeRuleDefaults.ruleSet(), withComponents(changedRules))
                                                              .get(DataComponents.ENCHANTABLE));
    }

    private static MixedBootsPair create(ItemStack first, ItemStack second) {
        return MixedBootsFactory.createPair(
                first, second, outputItem, mixedDataType, AttributeRuleDefaults.ruleSet(), ComponentRuleDefaults.state());
    }

    private static ComponentRules withComponents(ComponentRuleSet components) {
        ComponentRules defaults = ComponentRuleDefaults.state();
        return new ComponentRules(defaults.durability(), defaults.repairCost(), components);
    }

    private static ItemStack boots(Item item) {
        return boots(item, defaultEquippable());
    }

    private static ItemStack boots(Item item, Equippable equippable) {
        return new ItemStack(Holder.direct(item, baseComponents(equippable)), 1);
    }

    private static DataComponentMap baseComponents(Equippable equippable) {
        return DataComponentMap.builder()
                               .set(DataComponents.MAX_STACK_SIZE, 1)
                               .set(DataComponents.EQUIPPABLE, equippable)
                               .set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                               .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                               .build();
    }

    private static Equippable defaultEquippable() {
        return Equippable.builder(EquipmentSlot.FEET).build();
    }

    private static void setDurability(ItemStack stack, int maximum, int damage) {
        if (maximum > 0) {
            stack.set(DataComponents.MAX_DAMAGE, maximum);
            stack.set(DataComponents.DAMAGE, damage);
        }
    }

    private static long remaining(int maximum, int damage) {
        return Math.max(0L, Math.min((long) maximum - damage, maximum));
    }

    private static void assertRepairableAccepts(ItemStack stack, Item... expectedItems) {
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        assertNotNull(repairable);
        assertEquals(Set.of(expectedItems), repairable.items().stream()
                                                      .map(Holder::value)
                                                      .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static AttributeModifier modifier(
            Identifier id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(id, amount, operation);
    }

    private static Holder<Attribute> syntheticAttribute(String name) {
        MappedRegistry<Attribute> registry = new MappedRegistry<>(Registries.ATTRIBUTE, Lifecycle.stable());
        Holder<Attribute> attribute = registry.register(
                ResourceKey.create(Registries.ATTRIBUTE, Identifier.fromNamespaceAndPath("test", name)),
                new RangedAttribute("attribute.test." + name, 0.0, -Double.MAX_VALUE, Double.MAX_VALUE),
                RegistrationInfo.BUILT_IN);
        registry.freeze();
        return attribute;
    }

    private static void setEnchantment(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    private static Holder<Enchantment> syntheticEnchantment(
            String name, int maximumLevel, HolderSet<Enchantment> exclusiveSet) {
        MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        Holder<Enchantment> enchantment = registerEnchantment(registry, name, maximumLevel, exclusiveSet);
        registry.freeze();
        return enchantment;
    }

    private static Holder.Reference<Enchantment> registerEnchantment(
            MappedRegistry<Enchantment> registry,
            String name,
            int maximumLevel,
            HolderSet<Enchantment> exclusiveSet) {
        Enchantment.EnchantmentDefinition definition = Enchantment.definition(
                HolderSet.direct(Holder.direct(Items.IRON_BOOTS)),
                1,
                maximumLevel,
                Enchantment.constantCost(1),
                Enchantment.constantCost(1),
                1,
                EquipmentSlotGroup.FEET);
        Enchantment enchantment = new Enchantment(
                Component.literal(name), definition, exclusiveSet, DataComponentMap.EMPTY);
        return registry.register(
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("test", name)),
                enchantment,
                RegistrationInfo.BUILT_IN);
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }
}
