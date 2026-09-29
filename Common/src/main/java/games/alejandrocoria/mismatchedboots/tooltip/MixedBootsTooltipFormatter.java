package games.alejandrocoria.mismatchedboots.tooltip;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class MixedBootsTooltipFormatter {
    private static final String LEFT_KEY = "tooltip.mismatchedboots.mixed_boots.left";
    private static final String RIGHT_KEY = "tooltip.mismatchedboots.mixed_boots.right";
    private static final String NAME_WITH_NATURAL_KEY =
            "tooltip.mismatchedboots.mixed_boots.name_with_natural";
    private static final List<DataComponentType<?>> SOURCE_GAMEPLAY_COMPONENTS = List.of(
            DataComponents.DAMAGE,
            DataComponents.MAX_DAMAGE,
            DataComponents.REPAIR_COST,
            DataComponents.REPAIRABLE,
            DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.ENCHANTMENTS,
            DataComponents.STORED_ENCHANTMENTS,
            DataComponents.EQUIPPABLE,
            DataComponents.DAMAGE_RESISTANT,
            DataComponents.UNBREAKABLE,
            DataComponents.GLIDER,
            DataComponents.ENCHANTABLE,
            DataComponents.RARITY,
            DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

    private MixedBootsTooltipFormatter() {
    }

    public static void appendSourceSections(
            MixedBootsData data,
            Item.TooltipContext context,
            Consumer<Component> tooltip) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tooltip, "tooltip");

        appendSourceSection(LEFT_KEY, data.left(), context, tooltip);
        tooltip.accept(CommonComponents.EMPTY);
        appendSourceSection(RIGHT_KEY, data.right(), context, tooltip);
        tooltip.accept(CommonComponents.EMPTY);
    }

    private static void appendSourceSection(
            String sideKey,
            BootHalf half,
            Item.TooltipContext context,
            Consumer<Component> tooltip) {
        ItemStack source = half.source();
        tooltip.accept(Component.translatable(sideKey, sourceName(source)).withStyle(ChatFormatting.GRAY));

        TooltipDisplay sourceDisplay = source.getOrDefault(
                DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (sourceDisplay.hideTooltip()) {
            return;
        }

        appendSourceDetails(source, context, hideGameplayComponents(sourceDisplay), tooltip);
    }

    private static Component sourceName(ItemStack source) {
        Component customName = source.getCustomName();
        Component naturalName = source.getItemName();
        if (customName == null || customName.equals(naturalName)) {
            return source.getHoverName();
        }

        return Component.translatable(
                NAME_WITH_NATURAL_KEY,
                source.getHoverName(),
                naturalName.copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    private static TooltipDisplay hideGameplayComponents(TooltipDisplay display) {
        TooltipDisplay result = display;
        for (DataComponentType<?> component : SOURCE_GAMEPLAY_COMPONENTS) {
            result = result.withHidden(component, true);
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static void appendSourceDetails(
            ItemStack source,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip) {
        source.addDetailsToTooltip(context, display, null, TooltipFlag.NORMAL, tooltip);
    }
}
