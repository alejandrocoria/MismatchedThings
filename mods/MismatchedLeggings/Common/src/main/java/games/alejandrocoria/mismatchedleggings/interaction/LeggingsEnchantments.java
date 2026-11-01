package games.alejandrocoria.mismatchedleggings.interaction;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Future book admission is source-based; composition-time union is a separate rule. */
public final class LeggingsEnchantments {
    private LeggingsEnchantments() {
    }

    public static boolean canApply(Enchantment enchantment, ItemStack stack) {
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        return data != null && (enchantment.canEnchant(data.left().source())
                || enchantment.canEnchant(data.right().source()));
    }
}
