package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairableComposerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void omitsTheComponentWhenNeitherSourceIsRepairable() {
        ItemStack first = withoutRepairable(Items.IRON_BOOTS);
        ItemStack second = withoutRepairable(Items.GOLDEN_BOOTS);

        assertTrue(RepairableComposer.compose(first, second).isEmpty());
    }

    @Test
    void preservesTheOnlyAvailableRepairableComponent() {
        ItemStack first = withoutRepairable(Items.IRON_BOOTS);
        ItemStack second = withoutRepairable(Items.GOLDEN_BOOTS);
        first.set(DataComponents.REPAIRABLE, repairable(Items.IRON_INGOT, Items.IRON_NUGGET));

        assertEquals(Set.of(Items.IRON_INGOT, Items.IRON_NUGGET), acceptedItems(first, second));
        assertEquals(Set.of(Items.IRON_INGOT, Items.IRON_NUGGET), acceptedItems(second, first));
    }

    @Test
    void unionsBothComponentsWithoutDuplicatesOrInputMutation() {
        ItemStack first = withoutRepairable(Items.IRON_BOOTS);
        ItemStack second = withoutRepairable(Items.GOLDEN_BOOTS);
        first.set(DataComponents.REPAIRABLE, repairable(Items.IRON_INGOT, Items.GOLD_INGOT));
        second.set(DataComponents.REPAIRABLE, repairable(Items.GOLD_INGOT, Items.DIAMOND));
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();

        Set<Item> expected = Set.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND);
        assertEquals(expected, acceptedItems(first, second));
        assertEquals(expected, acceptedItems(second, first));
        assertTrue(ItemStack.matches(firstBefore, first));
        assertTrue(ItemStack.matches(secondBefore, second));
    }

    private static Set<Item> acceptedItems(ItemStack first, ItemStack second) {
        return RepairableComposer.compose(first, second).orElseThrow().items().stream()
                                 .map(Holder::value)
                                 .collect(Collectors.toUnmodifiableSet());
    }

    private static Repairable repairable(Item... items) {
        return new Repairable(HolderSet.direct(item -> Holder.direct(item), items));
    }

    private static ItemStack withoutRepairable(Item item) {
        return new ItemStack(Holder.direct(item, DataComponentMap.EMPTY), 1);
    }
}
