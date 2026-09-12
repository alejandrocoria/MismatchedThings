package games.alejandrocoria.mismatchedboots.interaction;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class MixedBootsAnvilPolicy {
    private MixedBootsAnvilPolicy() {
    }

    public static boolean rejectsCombination(ItemStack input, ItemStack addition) {
        return ModContent.isMixedBoots(input) && ModContent.isMixedBoots(addition);
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
