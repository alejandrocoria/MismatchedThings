package games.alejandrocoria.mismatchedleggings.composition;

import games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus.ForbiddenModifierAdapter;
import games.alejandrocoria.mismatchedleggings.component.ComponentProjection;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeInfusionService;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentValueMerger;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairableComposer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;

/** Refresh source-derived fields without recreating the live root from historical wallets. */
public final class MixedLeggingsRecomposer {
    private MixedLeggingsRecomposer() {
    }

    public static ItemStack recompose(ItemStack root, ItemStack newLeft, ItemStack newRight) {
        return recompose(root, newLeft, newRight, null);
    }

    public static ItemStack recompose(ItemStack root, ItemStack newLeft, ItemStack newRight,
                                      HolderLookup.@Nullable Provider registries) {
        var previous = MixedLeggingsStructure.validData(root)
                .orElseThrow(() -> new IllegalArgumentException("Recomposition requires a valid mixed leggings root"));
        var data = new MixedLeggingsData(new LeggingHalf(newLeft), new LeggingHalf(newRight));
        var left = data.left().source();
        var right = data.right().source();
        var rules = CompositionRuleState.server();
        var oldProjection = ComponentProjection.project(previous.left().source(), previous.right().source(), rules.components().components());
        var newProjection = ComponentProjection.project(left, right, rules.components().components());
        var output = root.copy();
        var types = Collections.newSetFromMap(new IdentityHashMap<DataComponentType<?>, Boolean>());
        types.addAll(oldProjection.managedTypes());
        types.addAll(newProjection.managedTypes());
        for (var type : types) {
            mergeComponent(root, oldProjection.primary(), newProjection.primary(), output, type);
        }

        int maximum = DurabilityMerger.merge(left, right, rules.components().durability()).primary().maximum();
        output.remove(DataComponents.MAX_DAMAGE);
        output.remove(DataComponents.DAMAGE);
        if (maximum > 0) {
            output.set(DataComponents.MAX_DAMAGE, maximum);
            output.set(DataComponents.DAMAGE, Math.clamp(root.getDamageValue(), 0, maximum));
        }
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, AttributeMerger.merge(
                MixedLeggingsFactory.queryAttributes(left), MixedLeggingsFactory.queryAttributes(right), rules.attributes()::policyFor).primary());
        output.remove(DataComponents.REPAIRABLE);
        RepairableComposer.compose(left, right).ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        // Live infusion is one tuple, even when a field equals the old source projection.
        NeoVitaeInfusionService.INSTANCE.preserveState(root, output);
        var finalized = ForbiddenModifierAdapter.INSTANCE.finish(output, registries);
        MixedLeggingsStructure.apply(finalized, data);
        return finalized;
    }

    private static <T> void mergeComponent(ItemStack root, DataComponentMap previous, DataComponentMap replacement,
                                          ItemStack output, DataComponentType<T> type) {
        // A live edit/removal wins. Only an unchanged old projection follows the transformed source.
        if (!ComponentValueMerger.equivalentValues(type, root.get(type), previous.get(type))) {
            return;
        }
        T value = replacement.get(type);
        if (value == null) {
            output.remove(type);
        } else {
            output.set(type, value);
        }
    }
}
