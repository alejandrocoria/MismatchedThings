package games.alejandrocoria.mismatchedboots.runtimetest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;

public final class NetworkAcceptanceStacks {
    public static final Identifier MOVEMENT_MODIFIER =
            Identifier.fromNamespaceAndPath("mismatchedboots-network-test", "shared_movement");

    private NetworkAcceptanceStacks() {
    }

    public static ItemStack firstSource() {
        ItemStack stack = source(Items.LEATHER_BOOTS, 2.0D);
        stack.setDamageValue(11);
        stack.set(DataComponents.REPAIR_COST, 2);
        stack.set(DataComponents.ENCHANTABLE, new Enchantable(8));
        return stack;
    }

    public static ItemStack secondSource() {
        ItemStack stack = source(Items.CHAINMAIL_BOOTS, 4.0D);
        stack.setDamageValue(7);
        stack.set(DataComponents.REPAIR_COST, 5);
        stack.set(DataComponents.ENCHANTABLE, new Enchantable(20));
        return stack;
    }

    public static String describePrimary(ItemStack stack) {
        if (!stack.is(ModContent.MIXED_BOOTS_KEY)) {
            throw new AssertionError("Crafted stack is not Mismatched Boots: " + stack);
        }
        MixedBootsData data = ModContent.getMixedBootsData(stack)
                                        .orElseThrow(() -> new AssertionError("Crafted stack has no source data"));
        if (!data.left().source().is(Items.LEATHER_BOOTS)
                || !data.right().source().is(Items.CHAINMAIL_BOOTS)) {
            throw new AssertionError("Crafted stack has the wrong orientation: " + data);
        }
        ItemAttributeModifiers.Entry entry = movementEntry(stack);
        if (!entry.modifier().id().equals(MOVEMENT_MODIFIER)
                || entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE
                || entry.slot() != EquipmentSlotGroup.FEET
                || Double.compare(entry.modifier().amount(), 6.0D) != 0) {
            throw new AssertionError("Unexpected composed movement modifier: " + entry);
        }
        assertComponents(stack, Items.LEATHER_BOOTS.getDefaultInstance().getMaxDamage(), 11, 2, 8);
        return "left=" + BuiltInRegistries.ITEM.getKey(data.left().source().getItem()) + '\n'
                + "right=" + BuiltInRegistries.ITEM.getKey(data.right().source().getItem()) + '\n'
                + "modifier=" + entry.modifier().id() + '\n'
                + "amount=" + entry.modifier().amount() + '\n'
                + "max_damage=" + stack.getMaxDamage() + '\n'
                + "damage=" + stack.getDamageValue() + '\n'
                + "repair_cost=" + stack.getOrDefault(DataComponents.REPAIR_COST, 0) + '\n'
                + "enchantable=" + stack.get(DataComponents.ENCHANTABLE).value() + '\n';
    }

    public static void assertInverse(ItemStack stack) {
        MixedBootsData data = ModContent.getMixedBootsData(stack)
                                        .orElseThrow(() -> new AssertionError("Crafting did not leave inverse Mismatched Boots"));
        if (!data.left().source().is(Items.CHAINMAIL_BOOTS)
                || !data.right().source().is(Items.LEATHER_BOOTS)) {
            throw new AssertionError("Crafting left the wrong inverse orientation: " + data);
        }
        movementEntry(stack);
        assertComponents(stack, Items.CHAINMAIL_BOOTS.getDefaultInstance().getMaxDamage(), 7, 5, 20);
    }

    private static void assertComponents(
            ItemStack stack, int maxDamage, int damage, int repairCost, int enchantable) {
        Enchantable value = stack.get(DataComponents.ENCHANTABLE);
        if (stack.getMaxDamage() != maxDamage
                || stack.getDamageValue() != damage
                || stack.getOrDefault(DataComponents.REPAIR_COST, 0) != repairCost
                || value == null
                || value.value() != enchantable) {
            throw new AssertionError("Unexpected composed components: " + stack.getComponents());
        }
    }

    private static ItemStack source(Item item, double amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                                                                            .add(Attributes.MOVEMENT_SPEED,
                                                                                 new AttributeModifier(MOVEMENT_MODIFIER,
                                                                                                       amount,
                                                                                                       AttributeModifier.Operation.ADD_VALUE),
                                                                                 EquipmentSlotGroup.FEET)
                                                                            .build());
        return stack;
    }

    private static ItemAttributeModifiers.Entry movementEntry(ItemStack stack) {
        var entries = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                           .modifiers().stream()
                           .filter(entry -> entry.attribute().equals(Attributes.MOVEMENT_SPEED))
                           .toList();
        if (entries.size() != 1) {
            throw new AssertionError("Expected exactly one movement modifier, found " + entries);
        }
        return entries.getFirst();
    }
}
