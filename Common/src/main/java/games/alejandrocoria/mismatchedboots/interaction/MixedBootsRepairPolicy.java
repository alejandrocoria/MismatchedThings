package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class MixedBootsRepairPolicy {
    private MixedBootsRepairPolicy() {
    }

    public static boolean canUseOrdinaryDonor(ItemStack mixed, ItemStack donor) {
        if (!ModContent.isMixedBoots(mixed) || ModContent.isMixedBoots(donor)
                || !mixed.isDamageableItem() || donor.isEmpty()) {
            return false;
        }

        MixedBootsData data = ModContent.getMixedBootsData(mixed).orElse(null);
        return data != null && (donor.getItem() == data.left().source().getItem()
                || donor.getItem() == data.right().source().getItem());
    }

    public static ItemStack restoreDurability(ItemStack result, ItemStack mixed, ItemStack donor) {
        if (result.isEmpty()) {
            return result;
        }

        int maximum = mixed.getMaxDamage();
        long remaining = Math.min(
                maximum,
                remaining(mixed) + remaining(donor) + maximum * 5L / 100L);

        result.set(DataComponents.MAX_DAMAGE, maximum);
        result.setDamageValue((int) Math.max(0L, (long) maximum - remaining));
        return result;
    }

    private static long remaining(ItemStack stack) {
        int maximum = Math.max(0, stack.getMaxDamage());
        return Math.max(0L, Math.min(maximum, (long) maximum - stack.getDamageValue()));
    }
}
