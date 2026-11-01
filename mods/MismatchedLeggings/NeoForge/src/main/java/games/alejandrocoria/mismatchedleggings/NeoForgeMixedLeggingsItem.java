package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class NeoForgeMixedLeggingsItem extends MixedLeggingsItem {
    public NeoForgeMixedLeggingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        return data != null && (data.left().source().supportsEnchantment(enchantment)
                || data.right().source().supportsEnchantment(enchantment));
    }
}
