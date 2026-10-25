package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

final class GameTestStacks {
    static final String EMPTY_STRUCTURE = "mismatchedboots-test:empty";

    private GameTestStacks() {
    }

    static MixedBootsPair pair(Item left, Item right) {
        return MixedBootsFactory.createPair(new ItemStack(left), new ItemStack(right));
    }

    static MixedBootsPair pair(ItemStack left, ItemStack right) {
        return MixedBootsFactory.createPair(left, right);
    }

    static ItemStack mixed(Item left, Item right) {
        return pair(left, right).primary();
    }

    static ItemStack feetItem(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        return stack;
    }

    static ItemStack ownerDivergentBoots(Item item, double amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(
                                                                                    Attributes.FOLLOW_RANGE,
                                                                                    new AttributeModifier(
                                                                                            Identifier.fromNamespaceAndPath("mismatchedboots-test",
                                                                                                                            "owner_divergence"),
                                                                                            amount,
                                                                                            AttributeModifier.Operation.ADD_VALUE),
                                                                                    EquipmentSlotGroup.FEET)
                                                                            .build());
        return stack;
    }

    static MixedBootsData data(ItemStack stack) {
        return ModContent.getMixedBootsData(stack)
                         .orElseThrow(() -> new AssertionError("Stack has no mixed boots data: " + stack));
    }

    static void assertOrientation(
            GameTestHelper helper,
            ItemStack stack,
            Item expectedLeft,
            Item expectedRight,
            String outputName) {
        helper.assertTrue(stack.is(ModContent.MIXED_BOOTS_KEY),
                          outputName + " is not the registered mixed boots item");
        helper.assertValueEqual(stack.getCount(), 1, outputName + " count");
        MixedBootsData data = data(stack);
        helper.assertTrue(data.left().source().is(expectedLeft),
                          outputName + " has the wrong left source: " + data.left().source());
        helper.assertTrue(data.right().source().is(expectedRight),
                          outputName + " has the wrong right source: " + data.right().source());
        helper.assertValueEqual(data.left().source().getCount(), 1, outputName + " left source count");
        helper.assertValueEqual(data.right().source().getCount(), 1, outputName + " right source count");
    }

    static void assertOwnerDivergentAttribute(
            GameTestHelper helper, ItemStack stack, double expectedAmount, String outputName) {
        var entries = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                           .modifiers().stream()
                           .filter(entry -> entry.attribute().equals(Attributes.FOLLOW_RANGE))
                           .toList();
        helper.assertValueEqual(entries.size(), 1, outputName + " divergent attribute entries");
        helper.assertValueEqual(entries.getFirst().modifier().id(),
                                Identifier.fromNamespaceAndPath("mismatchedboots-test", "owner_divergence"),
                                outputName + " divergent attribute ID");
        helper.assertTrue(Math.abs(entries.getFirst().modifier().amount() - expectedAmount) < 1.0E-12D,
                          outputName + " divergent attribute amount was " + entries.getFirst().modifier().amount());
    }
}
