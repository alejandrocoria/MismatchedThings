package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.compatibility.allthemodium.AllTheModiumLeggings;
import games.alejandrocoria.mismatchedleggings.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.rootsclassic.RootsTickCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCures;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffects;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearLiveState;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.function.Consumer;

public final class NeoForgeMixedLeggingsItem extends MixedLeggingsItem {
    public NeoForgeMixedLeggingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        SilentGearEffects.tick(stack, level, entity, slot);
        SilentGearLiveState.tick(stack, level, entity, slot);
        SilentGearWearer.tick(stack, level, entity, slot);
        SilentGearMagnet.tick(stack, level, entity, slot);
        SilentGearCures.tick(stack, level, entity, slot);
        AllTheModiumLeggings.tick(stack, level, entity, slot);
        RootsTickCompatibility.tick(stack, level, entity, slot);
        IceAndFireTideCompatibility.activate(stack, level, entity, slot);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> broken) {
        return SilentGearEffects.damage(stack, super.damageItem(stack, amount, entity, broken), entity);
    }

    @Override
    public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
        return SilentGearRecognition.brilliant(stack, wearer,
                super.makesPiglinsNeutral(stack, wearer) || AllTheModiumLeggings.eligible(stack));
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
