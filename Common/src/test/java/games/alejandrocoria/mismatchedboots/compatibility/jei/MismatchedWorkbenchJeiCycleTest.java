package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MismatchedWorkbenchJeiCycleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void candidatesAreVisibleValidUidUniqueCountOneAndStablySorted() {
        ItemStack secondIronVariant = named(boots(Items.IRON_BOOTS), "second iron");
        ItemStack firstIronVariant = named(boots(Items.IRON_BOOTS), "first iron");
        ItemStack duplicateFirstIron = firstIronVariant.copyWithCount(8);
        ItemStack hiddenLeather = named(boots(Items.LEATHER_BOOTS), "hidden");
        List<ItemStack> ingredients = List.of(
                secondIronVariant,
                boots(Items.DIAMOND_BOOTS),
                equippable(Items.IRON_HELMET, EquipmentSlot.HEAD),
                firstIronVariant,
                duplicateFirstIron,
                hiddenLeather);

        List<ItemStack> candidates = MismatchedWorkbenchJeiCycle.collectCandidates(
                ingredients,
                stack -> stack != hiddenLeather,
                stack -> stack.getHoverName().getString(),
                stack -> stack.getItem().toString());

        assertEquals(List.of(
                             Items.DIAMOND_BOOTS,
                             Items.IRON_BOOTS,
                             Items.IRON_BOOTS),
                     candidates.stream().map(ItemStack::getItem).toList());
        assertEquals(List.of("second iron", "first iron"),
                     candidates.subList(1, 3).stream().map(stack -> stack.getHoverName().getString()).toList());
        assertTrue(candidates.stream().allMatch(stack -> stack.getCount() == 1));
    }

    @Test
    void circularCycleKeepsAllSlotsAlignedAndPreservesInverseOrder() {
        List<ItemStack> boots = List.of(
                boots(Items.DIAMOND_BOOTS),
                boots(Items.GOLDEN_BOOTS),
                boots(Items.IRON_BOOTS));

        MismatchedWorkbenchJeiExample example = MismatchedWorkbenchJeiCycle.create(
                boots,
                MismatchedWorkbenchJeiCycleTest::orderedResult).orElseThrow();

        assertEquals(List.of(Items.DIAMOND_BOOTS, Items.GOLDEN_BOOTS, Items.IRON_BOOTS),
                     example.leftInputs().stream().map(ItemStack::getItem).toList());
        assertEquals(List.of(Items.GOLDEN_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS),
                     example.rightInputs().stream().map(ItemStack::getItem).toList());
        assertEquals(example.leftInputs().stream().map(ItemStack::getItem).toList(),
                     example.primaryOutputs().stream().map(ItemStack::getItem).toList());
        assertEquals(example.rightInputs().stream().map(ItemStack::getItem).toList(),
                     example.inverseOutputs().stream().map(ItemStack::getItem).toList());
    }

    @Test
    void emptyAndRejectedCyclesDoNotPublishAnExample() {
        assertTrue(MismatchedWorkbenchJeiCycle.create(List.of(),
                                                      MismatchedWorkbenchJeiCycleTest::orderedResult).isEmpty());
        assertTrue(MismatchedWorkbenchJeiCycle.create(
                List.of(boots(Items.IRON_BOOTS), boots(Items.GOLDEN_BOOTS)),
                (left, right) -> MismatchedWorkbenchResult.EMPTY).isEmpty());
    }

    @Test
    void oneCandidateFormsOneSelfPairAndRejectedAdjacentPairsAreSkipped() {
        ItemStack only = boots(Items.IRON_BOOTS);
        MismatchedWorkbenchJeiExample single = MismatchedWorkbenchJeiCycle.create(
                List.of(only),
                MismatchedWorkbenchJeiCycleTest::orderedResult).orElseThrow();
        assertEquals(1, single.leftInputs().size());
        assertEquals(Items.IRON_BOOTS, single.leftInputs().getFirst().getItem());
        assertEquals(Items.IRON_BOOTS, single.rightInputs().getFirst().getItem());

        List<ItemStack> boots = List.of(
                boots(Items.DIAMOND_BOOTS),
                boots(Items.GOLDEN_BOOTS),
                boots(Items.IRON_BOOTS));
        MismatchedWorkbenchJeiExample withRejectedPair = MismatchedWorkbenchJeiCycle.create(
                boots,
                (left, right) -> left.is(Items.GOLDEN_BOOTS)
                                 ? MismatchedWorkbenchResult.EMPTY
                                 : orderedResult(left, right)).orElseThrow();

        assertEquals(List.of(Items.DIAMOND_BOOTS, Items.IRON_BOOTS),
                     withRejectedPair.leftInputs().stream().map(ItemStack::getItem).toList());
        assertEquals(List.of(Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS),
                     withRejectedPair.rightInputs().stream().map(ItemStack::getItem).toList());
    }

    @Test
    void largeCycleComposesEachAdjacentPairExactlyOnce() {
        List<ItemStack> boots = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            boots.add(named(boots(Items.IRON_BOOTS), "variant " + index));
        }
        AtomicInteger calls = new AtomicInteger();

        Optional<MismatchedWorkbenchJeiExample> example = MismatchedWorkbenchJeiCycle.create(boots, (left, right) -> {
            calls.incrementAndGet();
            return orderedResult(left, right);
        });

        assertEquals(512, calls.get());
        assertEquals(512, example.orElseThrow().leftInputs().size());
    }

    private static MismatchedWorkbenchResult orderedResult(ItemStack left, ItemStack right) {
        return new MismatchedWorkbenchResult(left, right);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack boots(Item item) {
        return equippable(item, EquipmentSlot.FEET);
    }

    private static ItemStack equippable(Item item, EquipmentSlot slot) {
        DataComponentMap components = DataComponentMap.builder()
                                                      .set(DataComponents.MAX_STACK_SIZE, 1)
                                                      .set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build())
                                                      .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                                                      .build();
        return new ItemStack(Holder.direct(item, components), 1);
    }
}
