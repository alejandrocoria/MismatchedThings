package games.alejandrocoria.mismatchedleggings.interaction;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.ItemStack;

/** Donor identity comes from provenance; durability always comes from the live stacks. */
public final class LeggingsRepair {
    private LeggingsRepair() {
    }

    public static boolean acceptsDonor(ItemStack mixed, ItemStack donor) {
        var data = MixedLeggingsStructure.validData(mixed).orElse(null);
        return data != null && !donor.isEmpty() && donor.getCount() == 1
                && !MixedLeggingsStructure.isMixedItem(donor) && mixed.isDamageableItem()
                && (donor.getItem() == data.left().source().getItem()
                || donor.getItem() == data.right().source().getItem());
    }

    public static boolean anvilDonor(ItemStack input, ItemStack addition, boolean normallyAccepted) {
        return MixedLeggingsStructure.isMixedItem(input) ? acceptsDonor(input, addition) : normallyAccepted;
    }

    public static boolean anvilInput(ItemStack input, boolean normallyAccepted) {
        return normallyAccepted && (!MixedLeggingsStructure.isMixedItem(input)
                || MixedLeggingsStructure.validData(input).isPresent());
    }

    public static int craftingDamage(ItemStack mixed, ItemStack donor) {
        int maximum = mixed.getMaxDamage();
        long remaining = remaining(mixed) + remaining(donor) + maximum * 5L / 100L;
        return (int) Math.max(0L, (long) maximum - remaining);
    }

    private static long remaining(ItemStack stack) {
        int maximum = Math.max(0, stack.getMaxDamage());
        return Math.max(0L, Math.min(maximum, (long) maximum - stack.getDamageValue()));
    }
}
