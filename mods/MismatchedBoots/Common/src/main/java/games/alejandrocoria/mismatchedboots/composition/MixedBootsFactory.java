package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers;
import games.alejandrocoria.mismatchedboots.component.ComponentProjection;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.platform.Services;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityMerger;
import games.alejandrocoria.mismatchedthings.api.composition.EnchantmentMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairableComposer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MixedBootsFactory {
    private MixedBootsFactory() {
    }

    /** Gameplay entrypoint: client reset/packet timing must never change server composition. */
    public static MixedBootsPair createPair(ItemStack first, ItemStack second) {
        return createPair(first, second, (HolderLookup.Provider) null);
    }

    public static MixedBootsPair createPair(ItemStack first, ItemStack second, HolderLookup.@Nullable Provider registries) {
        return createPair(first, second, AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules(), registries);
    }

    /** Explicit immutable rules for client previews and other non-server composition. */
    public static MixedBootsPair createPair(
            ItemStack first, ItemStack second, AttributeRuleSet attributes, ComponentRules components) {
        return createPair(first, second, attributes, components, null);
    }

    public static MixedBootsPair createPair(
            ItemStack first, ItemStack second, AttributeRuleSet attributes, ComponentRules components,
            HolderLookup.@Nullable Provider registries) {
        Holder<Item> mixedBoots = BuiltInRegistries.ITEM
                .get(ModContent.MIXED_BOOTS_ID)
                .filter(holder -> holder.value() instanceof MixedBootsItem)
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots item is not registered"));
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                                                               .orElseThrow(() -> new IllegalStateException(
                                                                       "MismatchedBoots data component is not registered"));
        return createPair(first, second, mixedBoots, dataType, attributes, components, registries);
    }

    static MixedBootsPair createPair(
            ItemStack first, ItemStack second, Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules) {
        return createPair(first, second, mixedBoots, dataType, rules, componentRules, null);
    }

    private static MixedBootsPair createPair(
            ItemStack first, ItemStack second, Holder<Item> mixedBoots,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules,
            HolderLookup.@Nullable Provider registries) {
        BootHalf firstHalf = new BootHalf(first);
        BootHalf secondHalf = new BootHalf(second);
        ItemStack firstSource = firstHalf.source();
        ItemStack secondSource = secondHalf.source();
        var modifiers = ForbiddenModifierService.INSTANCE.inherit(firstSource, secondSource);

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
                attributes.primary(), enchantments, repairable, components.primary(), components.managedTypes(), rules, modifiers, registries);
        ItemStack inverse = createOrientation(
                secondHalf, firstHalf, mixedBoots, dataType, durability.inverse(), repairCost.inverse(),
                attributes.inverse(), enchantments, repairable, components.inverse(), components.managedTypes(), rules, modifiers, registries);

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
            AttributeRuleSet rules, ForbiddenModifiers modifiers, HolderLookup.@Nullable Provider registries) {
        ItemStack owner = left.source();
        ItemStack output = new ItemStack(mixedBoots, 1);

        List.copyOf(output.getComponents().keySet()).forEach(output::remove);
        output.applyComponents(owner.getComponents());

        output.remove(DataComponents.REPAIRABLE);
        repairable.ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        output.remove(dataType);

        MixedBootsStructuralProjection.applyIdentity(output);
        output.set(DataComponents.EQUIPPABLE, MixedBootsStructuralProjection.projectEquippable(owner, right.source()));

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
        ForbiddenBootsModifiers.initialize(output, modifiers);
        Services.PLATFORM.finalizeMixedBootsCreation(output, rules);
        ItemStack finalized = ForbiddenBootsModifiers.finish(output, registries);

        Optional<com.mojang.serialization.DataResult.Error<ItemStack>> error = ItemStack.validateStrict(finalized).error();
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid mixed boots output: " + error.get().message());
        }

        return finalized;
    }
}
