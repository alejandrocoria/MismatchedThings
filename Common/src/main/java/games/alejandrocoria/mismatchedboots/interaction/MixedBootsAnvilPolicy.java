package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class MixedBootsAnvilPolicy {
    private MixedBootsAnvilPolicy() {
    }

    public static boolean canUseOrdinaryDonor(
            ItemStack input,
            ItemStack addition,
            boolean normallyAccepted) {
        if (!ModContent.isMixedBoots(input)) {
            return normallyAccepted;
        }

        MixedBootsData data = ModContent.getMixedBootsData(input).orElse(null);
        if (data == null || ModContent.isMixedBoots(addition)) {
            return false;
        }

        return addition.getItem() == data.left().source().getItem()
                || addition.getItem() == data.right().source().getItem();
    }

    public static boolean canApplyEnchantment(
            Enchantment enchantment,
            ItemStack input,
            boolean normallyAllowed) {
        if (normallyAllowed) {
            return true;
        }

        MixedBootsData data = ModContent.getMixedBootsData(input).orElse(null);
        return data != null
                && (enchantment.canEnchant(data.left().source())
                || enchantment.canEnchant(data.right().source()));
    }
}
