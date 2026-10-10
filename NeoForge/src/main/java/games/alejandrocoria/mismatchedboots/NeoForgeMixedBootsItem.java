package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.function.Consumer;

public final class NeoForgeMixedBootsItem extends MixedBootsItem {
    public NeoForgeMixedBootsItem(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        JustDireTooltipCompatibility.appendRootState(stack, display, tooltip);
    }

    @Override
    protected Consumer<Component> sourceTooltip(ItemStack source, Consumer<Component> tooltip) {
        return JustDireTooltipCompatibility.sourceTooltip(source, tooltip);
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
