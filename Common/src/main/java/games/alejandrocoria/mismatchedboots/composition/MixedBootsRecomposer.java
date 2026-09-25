package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentProjection;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;

import java.util.Optional;

public final class MixedBootsRecomposer {
    private MixedBootsRecomposer() {
    }

    public static ItemStack recompose(ItemStack root, ItemStack newLeft, ItemStack newRight) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots data component is not registered"));
        AttributeRuleSet attributeRules = AttributeRuleState.current();
        ComponentRules componentRules = ComponentRuleState.current();
        return recompose(root, newLeft, newRight, dataType, attributeRules, componentRules);
    }

    static ItemStack recompose(
            ItemStack root,
            ItemStack newLeft,
            ItemStack newRight,
            DataComponentType<MixedBootsData> dataType,
            AttributeRuleSet rules) {
        return recompose(root, newLeft, newRight, dataType, rules, ComponentRuleDefaults.state());
    }

    static ItemStack recompose(
            ItemStack root, ItemStack newLeft, ItemStack newRight,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRuleSet componentRules) {
        ComponentRules defaults = ComponentRuleDefaults.state();
        return recompose(root, newLeft, newRight, dataType, rules,
                new ComponentRules(defaults.durability(), defaults.repairCost(), componentRules));
    }

    static ItemStack recompose(
            ItemStack root, ItemStack newLeft, ItemStack newRight,
            DataComponentType<MixedBootsData> dataType, AttributeRuleSet rules, ComponentRules componentRules) {
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
        ItemStack output = OwnerComponentMerger.merge(root, oldProjection, newProjection);
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
        output.set(DataComponents.EQUIPPABLE, MixedBootsFactory.projectEquippable(leftSource));
        output.set(DataComponents.MAX_STACK_SIZE, 1);
        output.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"));
        output.set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID);
        output.remove(DataComponents.REPAIRABLE);
        Optional<Repairable> repairable = RepairableComposer.compose(leftSource, rightSource);
        repairable.ifPresent(value -> output.set(DataComponents.REPAIRABLE, value));
        output.set(dataType, new MixedBootsData(left, right));

        var error = ItemStack.validateStrict(output).error();
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid recomposed mixed boots: " + error.get().message());
        }
        return output;
    }
}
