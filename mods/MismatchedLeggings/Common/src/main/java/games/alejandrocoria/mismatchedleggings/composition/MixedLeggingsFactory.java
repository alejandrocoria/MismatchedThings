package games.alejandrocoria.mismatchedleggings.composition;

import games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus.ForbiddenModifierAdapter;
import games.alejandrocoria.mismatchedleggings.component.ComponentProjection;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeInfusionService;
import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityMerger;
import games.alejandrocoria.mismatchedthings.api.composition.EnchantmentMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairableComposer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

public final class MixedLeggingsFactory {
    private static final CompositionAttributes ATTRIBUTES = ServiceLoader.load(CompositionAttributes.class).findFirst()
            .orElseThrow(() -> new IllegalStateException("No Leggings composition attribute adapter"));

    private MixedLeggingsFactory() {
    }

    public static MixedLeggingsPair createPair(ItemStack first, ItemStack second) {
        return createPair(first, second, CompositionRuleState.server());
    }

    /** Explicit snapshots keep future client previews independent of server authority. */
    public static MixedLeggingsPair createPair(ItemStack first, ItemStack second, CompositionRules rules) {
        return createPair(first, second, rules, null);
    }

    public static MixedLeggingsPair createPair(ItemStack first, ItemStack second, CompositionRules rules,
                                               HolderLookup.@Nullable Provider registries) {
        var firstHalf = new LeggingHalf(first);
        var secondHalf = new LeggingHalf(second);
        var firstSource = firstHalf.source();
        var secondSource = secondHalf.source();
        var components = ComponentProjection.project(firstSource, secondSource, rules.components().components());
        var durability = DurabilityMerger.merge(firstSource, secondSource, rules.components().durability());
        var repairCost = RepairCostMerger.merge(firstSource, secondSource, rules.components().repairCost());
        var attributes = AttributeMerger.merge(queryAttributes(firstSource), queryAttributes(secondSource), rules.attributes()::policyFor);
        var enchantments = EnchantmentMerger.merge(firstSource, secondSource);
        var repairable = RepairableComposer.compose(firstSource, secondSource);
        var modifiers = ForbiddenModifierAdapter.INSTANCE.inherit(firstSource, secondSource);

        var primary = createOrientation(new MixedLeggingsData(firstHalf, secondHalf), components.primary(), components.managedTypes(),
                durability.primary(), repairCost.primary(), attributes.primary(), enchantments, repairable, modifiers, registries);
        var inverse = createOrientation(new MixedLeggingsData(secondHalf, firstHalf), components.inverse(), components.managedTypes(),
                durability.inverse(), repairCost.inverse(), attributes.inverse(), enchantments, repairable, modifiers, registries);
        return new MixedLeggingsPair(primary, inverse);
    }

    static ItemAttributeModifiers queryAttributes(ItemStack source) {
        return ATTRIBUTES.query(source);
    }

    private static ItemStack createOrientation(MixedLeggingsData data, DataComponentMap projection, Set<DataComponentType<?>> managed,
                                               DurabilityMerger.Result durability, int repairCost, ItemAttributeModifiers attributes,
                                               ItemEnchantments enchantments, Optional<Repairable> repairable,
                                               ForbiddenModifiers modifiers, HolderLookup.@Nullable Provider registries) {
        var output = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        List.copyOf(output.getComponents().keySet()).forEach(output::remove);
        output.applyComponents(data.left().source().getComponents());

        // An omitted managed field is an explicit removal, not permission to keep the copied owner's value.
        managed.forEach(output::remove);
        output.applyComponents(projection);
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
        output.remove(DataComponents.REPAIRABLE);
        repairable.ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
        output.set(DataComponents.ENCHANTMENTS, enchantments);
        // Never accept product-owned authority copied from an arbitrary source's components.
        output.remove(LeggingsContent.forbiddenModifiersType());
        if (!modifiers.isEmpty()) {
            output.set(LeggingsContent.forbiddenModifiersType(), modifiers);
        }
        NeoVitaeInfusionService.INSTANCE.initializeCreation(output, data.left().source(), data.right().source());
        RogdoriumInfusionService.INSTANCE.initializeCreation(output, data.left().source(), data.right().source());
        var finalized = ForbiddenModifierAdapter.INSTANCE.finish(output, registries);
        // Structural installation is last and strictly validates the complete native stack.
        MixedLeggingsStructure.apply(finalized, data);
        return finalized;
    }
}
