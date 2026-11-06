package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers;
import games.alejandrocoria.mismatchedboots.component.ComponentProjection;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.platform.Services;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairableComposer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class MixedBootsRecomposer {
    private MixedBootsRecomposer() {
    }

    /** Gameplay entrypoint, independent of integrated-client synchronization state. */
    public static ItemStack recompose(ItemStack root, ItemStack newLeft, ItemStack newRight) {
        return recompose(root, newLeft, newRight, (HolderLookup.Provider) null);
    }

    public static ItemStack recompose(
            ItemStack root, ItemStack newLeft, ItemStack newRight, HolderLookup.@Nullable Provider registries) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                                                               .orElseThrow(() -> new IllegalStateException(
                                                                       "MismatchedBoots data component is not registered"));
        AttributeRuleSet attributeRules = AttributeRulesLifecycle.serverRules();
        ComponentRules componentRules = ComponentRulesLifecycle.serverRules();

        return recompose(root, newLeft, newRight, dataType, attributeRules, componentRules, registries);
    }

    static ItemStack recompose(
            ItemStack root, ItemStack newLeft, ItemStack newRight,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules) {
        return recompose(root, newLeft, newRight, dataType, rules, componentRules, null);
    }

    private static ItemStack recompose(
            ItemStack root, ItemStack newLeft, ItemStack newRight,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules,
            HolderLookup.@Nullable Provider registries) {
        if (ModContent.suspendedBootsData(root).isPresent()) {
            throw new IllegalArgumentException("Suspended mixed boots cannot be recomposed");
        }
        ForbiddenBootsModifiers.requireAuthority(root);

        MixedBootsData previous = Optional.ofNullable(root.get(dataType))
                                          .orElseThrow(() -> new IllegalArgumentException("Missing mixed boots data"));
        BootHalf left = new BootHalf(newLeft);
        BootHalf right = new BootHalf(newRight);
        ItemStack leftSource = left.source();
        ItemStack rightSource = right.source();

        ComponentProjection.Result oldProjection = ComponentProjection.project(
                previous.left().source(), previous.right().source(), componentRules.components());
        ComponentProjection.Result newProjection = ComponentProjection.project(
                leftSource, rightSource, componentRules.components());
        ItemStack output = ProjectedComponentMerger.merge(root, oldProjection, newProjection);
        int maximum = DurabilityMerger.merge(
                leftSource, rightSource, componentRules.durability()).primary().maximum();

        output.remove(DataComponents.MAX_DAMAGE);
        output.remove(DataComponents.DAMAGE);
        if (maximum > 0) {
            output.set(DataComponents.MAX_DAMAGE, maximum);
            output.set(DataComponents.DAMAGE, Math.clamp(root.getDamageValue(), 0, maximum));
        }

        output.set(DataComponents.ATTRIBUTE_MODIFIERS,
                   AttributeComposer.compose(leftSource, rightSource, rules).primary());
        output.set(DataComponents.EQUIPPABLE, MixedBootsStructuralProjection.projectEquippable(leftSource, rightSource));
        MixedBootsStructuralProjection.applyIdentity(output);
        output.remove(DataComponents.REPAIRABLE);
        Optional<Repairable> repairable = RepairableComposer.compose(leftSource, rightSource);
        repairable.ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        output.set(dataType, new MixedBootsData(left, right));
        Services.PLATFORM.finalizeMixedBootsRecomposition(root, output, rules);
        // JDT and generic recomposition may rebuild finite durability; FA is the final live projection.
        ItemStack finalized = ForbiddenBootsModifiers.finish(output, registries);

        var error = ItemStack.validateStrict(finalized).error();
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid recomposed mixed boots: " + error.get().message());
        }

        return finalized;
    }
}
