package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.compatibility.allthemodium.AllTheModiumLeggings;
import games.alejandrocoria.mismatchedleggings.compatibility.rootsclassic.RootsTickCompatibility;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class NeoForgeMixedLeggingsItem extends MixedLeggingsItem {
    public NeoForgeMixedLeggingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        AllTheModiumLeggings.tick(stack, level, entity, slot);
        RootsTickCompatibility.tick(stack, level, entity, slot);
    }

    @Override
    public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
        return super.makesPiglinsNeutral(stack, wearer) || AllTheModiumLeggings.eligible(stack);
    }

    @Override
    public boolean canGrindstoneRepairWith(ItemStack stack, ItemStack donor) {
        return super.canGrindstoneRepairWith(stack, donor)
                && stack.isCombineRepairable() && donor.isCombineRepairable();
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        return data != null && (data.left().source().supportsEnchantment(enchantment)
                || data.right().source().supportsEnchantment(enchantment));
    }
}
