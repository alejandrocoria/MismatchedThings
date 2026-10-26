package games.alejandrocoria.mismatchedboots.composition;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Map;
import java.util.TreeMap;

final class EnchantmentMerger {
    private EnchantmentMerger() {
    }

    static ItemEnchantments merge(ItemStack left, ItemStack right) {
        ItemEnchantments leftEnchantments = left.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments rightEnchantments = right.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Map<Identifier, Holder<Enchantment>> ordered = new TreeMap<>();

        addAll(ordered, leftEnchantments);
        addAll(ordered, rightEnchantments);

        ItemEnchantments.Mutable result = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : ordered.values()) {
            int leftLevel = leftEnchantments.getLevel(enchantment);
            int rightLevel = rightEnchantments.getLevel(enchantment);
            result.set(enchantment, mergeLevel(enchantment, leftLevel, rightLevel));
        }

        return result.toImmutable();
    }

    private static void addAll(
            Map<Identifier, Holder<Enchantment>> ordered, ItemEnchantments enchantments) {
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            Identifier id = enchantment.unwrapKey()
                                       .map(key -> key.identifier())
                                       .orElseThrow(() -> new IllegalArgumentException("Source uses an unregistered enchantment"));
            ordered.put(id, enchantment);
        }
    }

    private static int mergeLevel(Holder<Enchantment> enchantment, int leftLevel, int rightLevel) {
        if (leftLevel == 0) {
            return rightLevel;
        }

        if (rightLevel == 0 || leftLevel != rightLevel) {
            return Math.max(leftLevel, rightLevel);
        }

        long upgraded = (long) leftLevel + 1L;
        long allowedMaximum = (long) enchantment.value().getMaxLevel() + 1L;
        return (int) Math.min(255L, Math.min(upgraded, allowedMaximum));
    }
}
