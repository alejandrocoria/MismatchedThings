package games.alejandrocoria.mismatchedleggings.interaction;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.composition.EnchantmentMerger;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Bound duplicated composition value before Minecraft performs its normal take-time random roll. */
public final class LeggingsGrindstoneXp {
    private LeggingsGrindstoneXp() {
    }

    public static int adjustPotential(ItemStack input, int nativePotential) {
        if (!MixedLeggingsStructure.isMixedItem(input)) {
            return nativePotential;
        }
        var data = MixedLeggingsStructure.validData(input).orElse(null);
        if (data == null || nativePotential <= 0) {
            return 0;
        }

        var left = data.left().source();
        var right = data.right().source();
        int baseline = potential(EnchantmentMerger.merge(left, right));
        long sources = (long) potential(EnchantmentHelper.getEnchantmentsForCrafting(left))
                + potential(EnchantmentHelper.getEnchantmentsForCrafting(right));
        // A merged level can have a nonlinear cost: halving live value alone could mint XP.
        long duplicated = Math.min(nativePotential, Math.min(baseline, sources)) / 2L;
        long later = Math.max(0L, (long) nativePotential - baseline);
        return (int) Math.min(Integer.MAX_VALUE, duplicated + later);
    }

    private static int potential(ItemEnchantments enchantments) {
        long total = 0;
        for (var enchantment : enchantments.keySet()) {
            if (!enchantment.is(EnchantmentTags.CURSE)) {
                int level = enchantments.getLevel(enchantment);
                if (level > 0) {
                    total += Math.max(0, enchantment.value().getMinCost(level));
                    if (total >= Integer.MAX_VALUE) {
                        return Integer.MAX_VALUE;
                    }
                }
            }
        }
        return (int) total;
    }
}
