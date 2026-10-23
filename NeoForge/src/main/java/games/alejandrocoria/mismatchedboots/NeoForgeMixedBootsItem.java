package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.rootsclassic.RootsTickCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCures;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearEffects;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearLiveState;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearer;
import games.alejandrocoria.mismatchedboots.compatibility.undergarden.UndergardenBoots;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
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
    public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity entity) {
        return SilentGearRecognition.brilliant(stack, entity, super.makesPiglinsNeutral(stack, entity));
    }

    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
        // SG's optional slot override is broken for its own boots; use our resolved FEET source view.
        return super.canWalkOnPowderedSnow(stack, wearer) || SilentGearRecognition.snowWalker(stack)
                || UndergardenBoots.snow(stack);
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !(source.is(DamageTypeTags.IS_FIRE) && JustDireLavaRepairCompatibility.eligible(stack))
                && super.canBeHurtBy(stack, source);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        JustDireLavaRepairCompatibility.tick(stack, entity);
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        JustDireTooltipCompatibility.appendRootState(stack, display, tooltip);
    }

    @Override
    protected Consumer<Component> sourceTooltip(ItemStack source, TooltipFlag flag, Consumer<Component> tooltip) {
        SilentGearTooltipCompatibility.appendSource(source, flag, tooltip);
        AetherTooltipCompatibility.source(source, flag, tooltip);
        return JustDireTooltipCompatibility.sourceTooltip(source, tooltip);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        MysticalAugmentsCompatibility.tick(stack, level, entity, slot);
        IceAndFireTideCompatibility.activate(stack, level, entity, slot);
        RootsTickCompatibility.tick(stack, level, entity, slot);
        SilentGearEffects.tick(stack, level, entity, slot);
        SilentGearLiveState.tick(stack, level, entity, slot);
        SilentGearWearer.tick(stack, level, entity, slot);
        SilentGearMagnet.tick(stack, level, entity, slot);
        SilentGearCures.tick(stack, level, entity, slot);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> broken) {
        return SilentGearEffects.damage(stack, super.damageItem(stack, amount, entity, broken), entity);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        // NeoForge anvils use this hook: the root's armor tag must not bypass source eligibility.
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        return stack.getCount() == 1 && data != null
                && (data.left().source().supportsEnchantment(enchantment)
                || data.right().source().supportsEnchantment(enchantment));
    }
}
