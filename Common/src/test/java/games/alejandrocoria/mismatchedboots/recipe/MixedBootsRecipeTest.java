package games.alejandrocoria.mismatchedboots.recipe;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsRecipeTest {
    private static DataComponentType<MixedBootsData> mixedDataType;
    private static MixedBootsRecipe recipe;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        mixedDataType = ModContent.createMixedBootsDataType();
        recipe = new MixedBootsRecipe((first, second) ->
                new MixedBootsPair(first.copy(), second.copy()));
    }

    @ParameterizedTest
    @CsvSource({
        "2,2,0,1",
        "2,2,0,2",
        "2,2,0,3",
        "2,2,1,2",
        "3,3,0,1",
        "3,3,0,2",
        "3,3,0,3",
        "3,3,0,4",
        "3,3,1,7",
        "3,3,0,8",
        "3,3,2,6"
    })
    void acceptsLayoutsAndUsesReadingOrder(
            int width, int height, int firstSlot, int secondSlot) {
        assertOrientation(width, height, firstSlot, secondSlot, false);
        assertOrientation(width, height, firstSlot, secondSlot, true);
    }

    @Test
    void acceptsSameItemPairsWithDifferentComponents() {
        ItemStack first = boots(Items.IRON_BOOTS);
        ItemStack second = boots(Items.IRON_BOOTS);
        second.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Second"));
        CraftingInput input = input(2, 2, 0, first, 3, second);

        assertTrue(recipe.matches(input, null));
        assertEquals(first.getHoverName(), recipe.assemble(input).getHoverName());
        assertEquals(second.getHoverName(), onlyRemainder(recipe.getRemainingItems(input)).getHoverName());
    }

    @Test
    void rejectsEveryInvalidInputShape() {
        ItemStack iron = boots(Items.IRON_BOOTS);
        ItemStack gold = boots(Items.GOLDEN_BOOTS);

        assertFalse(recipe.matches(input(2, 2, 0, iron), null));
        assertFalse(recipe.matches(input(2, 2, 0, iron, 1, gold, 2, boots(Items.DIAMOND_BOOTS)), null));
        ItemStack nonFeetItem = boots(Items.DIAMOND_BOOTS);
        nonFeetItem.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build());
        assertFalse(recipe.matches(input(2, 2, 0, iron, 3, nonFeetItem), null));

        ItemStack stacked = boots(Items.IRON_BOOTS);
        stacked.setCount(2);
        assertFalse(recipe.matches(input(2, 2, 0, stacked, 3, gold), null));

        ItemStack mixed = boots(Items.DIAMOND_BOOTS);
        mixed.set(mixedDataType, new MixedBootsData(new BootHalf(iron), new BootHalf(gold)));
        assertFalse(recipe.matches(input(2, 2, 0, iron, 3, mixed), null));
        assertFalse(recipe.matches(input(2, 2, 0, mixed, 3, mixed.copy()), null));
    }

    @Test
    void invalidInputsNeverAssembleOrReturnRemainders() {
        CraftingInput input = input(2, 2, 0, boots(Items.IRON_BOOTS));

        assertTrue(recipe.assemble(input).isEmpty());
        assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void compositionFailuresNeverExposeEitherOutput() {
        MixedBootsRecipe failingRecipe = new MixedBootsRecipe((first, second) -> {
            throw new IllegalArgumentException("unsupported source component");
        });
        CraftingInput input = input(
                2, 2, 0, boots(Items.IRON_BOOTS), 3, boots(Items.GOLDEN_BOOTS));

        assertFalse(failingRecipe.matches(input, null));
        assertTrue(failingRecipe.assemble(input).isEmpty());
        assertTrue(failingRecipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void exposesTheRegisteredStatelessSerializer() {
        assertSame(MixedBootsRecipe.SERIALIZER, recipe.getSerializer());
        assertSame(MixedBootsRecipe.SERIALIZER, ModContent.createMixedBootsRecipeSerializer());
    }

    private static void assertOrientation(
            int width,
            int height,
            int firstSlot,
            int secondSlot,
            boolean reverseSources) {
        ItemStack earlier = boots(reverseSources ? Items.GOLDEN_BOOTS : Items.IRON_BOOTS);
        ItemStack later = boots(reverseSources ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS);
        CraftingInput input = input(width, height, firstSlot, earlier, secondSlot, later);

        assertTrue(recipe.matches(input, null));
        assertEquals(earlier.getItem(), recipe.assemble(input).getItem());

        NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
        int earlierInputIndex = indexOfItem(input, earlier.getItem());
        assertEquals(later.getItem(), remainders.get(earlierInputIndex).getItem());
        assertEquals(1, remainders.stream().filter(stack -> !stack.isEmpty()).count());
    }

    private static CraftingInput input(int width, int height, Object... slotAndStacks) {
        List<ItemStack> stacks = new ArrayList<>(java.util.Collections.nCopies(width * height, ItemStack.EMPTY));
        for (int index = 0; index < slotAndStacks.length; index += 2) {
            stacks.set((int) slotAndStacks[index], (ItemStack) slotAndStacks[index + 1]);
        }
        return CraftingInput.of(width, height, stacks);
    }

    private static ItemStack boots(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        return stack;
    }

    private static int indexOfItem(CraftingInput input, Item item) {
        for (int index = 0; index < input.size(); index++) {
            if (input.getItem(index).is(item)) {
                return index;
            }
        }
        throw new AssertionError("Item was not retained in the positioned crafting input");
    }

    private static ItemStack onlyRemainder(NonNullList<ItemStack> remainders) {
        List<ItemStack> present = remainders.stream().filter(stack -> !stack.isEmpty()).toList();
        assertEquals(1, present.size());
        return present.getFirst();
    }
}
