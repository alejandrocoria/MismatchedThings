package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class NeoForgeMixedBootsItem extends MixedBootsItem {
    public NeoForgeMixedBootsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        IceAndFireTideCompatibility.activate(stack, level, entity, slot);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (super.supportsEnchantment(stack, enchantment)) {
            return true;
        }
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        return data != null
                && (data.left().source().supportsEnchantment(enchantment)
                || data.right().source().supportsEnchantment(enchantment));
    }
}
