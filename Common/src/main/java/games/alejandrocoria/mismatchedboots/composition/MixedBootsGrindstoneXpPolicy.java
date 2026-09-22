package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class MixedBootsGrindstoneXpPolicy {
    private MixedBootsGrindstoneXpPolicy() {
    }

    public static int adjustPotential(ItemStack input, int vanillaPotential) {
        if (!ModContent.isMixedBoots(input)) {
            return vanillaPotential;
        }

        MixedBootsData data = ModContent.getMixedBootsData(input).orElse(null);
        if (data == null) {
            return 0;
        }

        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        int baseline = potential(EnchantmentMerger.merge(left, right));
        long sources = (long) potential(EnchantmentHelper.getEnchantmentsForCrafting(left))
                + potential(EnchantmentHelper.getEnchantmentsForCrafting(right));
        return boundedPotential(vanillaPotential, baseline, sources);
    }

    static int boundedPotential(int root, int baseline, long sources) {
        if (root <= 0 || baseline < 0 || sources < 0) {
            return 0;
        }
        long live = root;
        long initial = baseline;
        long original = sources;
        long duplicated = Math.min(live, Math.min(initial, original)) / 2L;
        long later = Math.max(0L, live - initial);
        return (int) Math.min(Integer.MAX_VALUE, duplicated + later);
    }

    private static int potential(ItemEnchantments enchantments) {
        long total = 0L;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
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
