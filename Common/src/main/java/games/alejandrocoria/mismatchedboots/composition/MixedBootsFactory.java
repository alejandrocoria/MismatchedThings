package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentProjection;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MixedBootsFactory {
    private MixedBootsFactory() {
    }

    /** Gameplay entrypoint: client reset/packet timing must never change server composition. */
    public static MixedBootsPair createPair(ItemStack first, ItemStack second) {
        return createPair(first, second, AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules());
    }

    /** Explicit immutable rules for client previews and other non-server composition. */
    public static MixedBootsPair createPair(
            ItemStack first, ItemStack second, AttributeRuleSet attributes, ComponentRules components) {
        Holder<Item> mixedBoots = BuiltInRegistries.ITEM
                .get(ModContent.MIXED_BOOTS_ID)
                .filter(holder -> holder.value() instanceof MixedBootsItem)
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots item is not registered"));
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots data component is not registered"));
        return createPair(first, second, mixedBoots, dataType, attributes, components);
    }

    static MixedBootsPair createPair(
            ItemStack first, ItemStack second, Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules) {
        BootHalf firstHalf = new BootHalf(first);
        BootHalf secondHalf = new BootHalf(second);
        ItemStack firstSource = firstHalf.source();
        ItemStack secondSource = secondHalf.source();
        ComponentProjection.Result components = ComponentProjection.project(
                firstSource, secondSource, componentRules.components());
        DurabilityMerger.Results durability = DurabilityMerger.merge(
                firstSource, secondSource, componentRules.durability());
        RepairCostMerger.Results repairCost = RepairCostMerger.merge(
                firstSource, secondSource, componentRules.repairCost());
        AttributeComposer.Result attributes = AttributeComposer.compose(firstSource, secondSource, rules);
        ItemEnchantments enchantments = EnchantmentMerger.merge(firstSource, secondSource);
        Optional<Repairable> repairable = RepairableComposer.compose(firstSource, secondSource);

        ItemStack primary = createOrientation(
                firstHalf, secondHalf, mixedBoots, dataType, durability.primary(), repairCost.primary(),
                attributes.primary(), enchantments, repairable, components.primary(), components.managedTypes(), rules);
        ItemStack inverse = createOrientation(
                secondHalf, firstHalf, mixedBoots, dataType, durability.inverse(), repairCost.inverse(),
                attributes.inverse(), enchantments, repairable, components.inverse(), components.managedTypes(), rules);
        return new MixedBootsPair(primary, inverse);
    }

    private static ItemStack createOrientation(
            BootHalf left,
            BootHalf right,
            Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType,
            DurabilityMerger.Result durability,
            int repairCost,
            ItemAttributeModifiers attributes,
            ItemEnchantments enchantments,
            Optional<Repairable> repairable,
            DataComponentMap components,
            Set<DataComponentType<?>> projectedTypes,
            AttributeRuleSet rules) {
        ItemStack owner = left.source();
        ItemStack output = new ItemStack(mixedBoots, 1);

        List.copyOf(output.getComponents().keySet()).forEach(output::remove);
        output.applyComponents(owner.getComponents());

        output.remove(DataComponents.REPAIRABLE);
        repairable.ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        output.remove(dataType);

        output.set(DataComponents.MAX_STACK_SIZE, 1);
        output.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"));
        output.set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID);
        output.set(DataComponents.EQUIPPABLE, projectEquippable(owner));

        output.remove(DataComponents.MAX_DAMAGE);
        output.remove(DataComponents.DAMAGE);
        if (durability.damageable()) {
            output.set(DataComponents.MAX_DAMAGE, durability.maximum());
            output.set(DataComponents.DAMAGE, durability.damage());
        }

        output.remove(DataComponents.REPAIR_COST);
        if (repairCost != 0) {
            output.set(DataComponents.REPAIR_COST, repairCost);
        }

        output.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        output.set(DataComponents.ENCHANTMENTS, enchantments);
        projectedTypes.forEach(output::remove);
        output.applyComponents(components);
        output.set(dataType, new MixedBootsData(left, right));
        Services.PLATFORM.finalizeMixedBootsCreation(output, rules);

        Optional<com.mojang.serialization.DataResult.Error<ItemStack>> error = ItemStack.validateStrict(output).error();
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid mixed boots output: " + error.get().message());
        }
        return output;
    }

    static Equippable projectEquippable(ItemStack owner) {
        Equippable source = owner.get(DataComponents.EQUIPPABLE);
        if (source == null) {
            throw new IllegalArgumentException("Mixed boots owner is not feet-equippable");
        }
        return new Equippable(
                EquipmentSlot.FEET,
                source.equipSound(),
                source.assetId(),
                Optional.empty(),
                source.allowedEntities(),
                source.dispensable(),
                source.swappable(),
                source.damageOnHurt(),
                source.equipOnInteract(),
                source.canBeSheared(),
                source.shearingSound());
    }
}
