package games.alejandrocoria.mismatchedthings.api.composition;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionOperationsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
    void separateDurabilityClampsEachOwnerWithoutMutatingInputs() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.GOLDEN_BOOTS);
        setDurability(first, 101, 150);
        ItemStack before = first.copy();

        DurabilityMerger.Results result = DurabilityMerger.merge(first, second, DurabilityPolicy.SEPARATE);

        assertEquals(new DurabilityMerger.Result(101, 101), result.primary());
        assertEquals(new DurabilityMerger.Result(0, 0), result.inverse());
        assertTrue(ItemStack.matches(before, first));
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
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
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

    private static ItemStack boots(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), 1);
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
}
