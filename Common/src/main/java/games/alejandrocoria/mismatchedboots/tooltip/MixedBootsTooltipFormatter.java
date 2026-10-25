package games.alejandrocoria.mismatchedboots.tooltip;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
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
            TooltipFlag flag,
            Consumer<Component> tooltip) {
        appendSourceSections(data, context, flag, tooltip, (source, output) -> output);
    }

    /** Adapts only nested source details; identities, separators and root details retain their owners. */
    public static void appendSourceSections(
            MixedBootsData data,
            Item.TooltipContext context,
            TooltipFlag flag,
            Consumer<Component> tooltip,
            BiFunction<ItemStack, Consumer<Component>, Consumer<Component>> sourceTooltips) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(sourceTooltips, "sourceTooltips");

        appendSourceSection(LEFT_KEY, data.left(), context, flag, tooltip, sourceTooltips);
        tooltip.accept(TooltipSeparator.INSTANCE);
        appendSourceSection(RIGHT_KEY, data.right(), context, flag, tooltip, sourceTooltips);
        tooltip.accept(TooltipSeparator.INSTANCE);
    }

    private static void appendSourceSection(
            String sideKey,
            BootHalf half,
            Item.TooltipContext context,
            TooltipFlag flag,
            Consumer<Component> tooltip,
            BiFunction<ItemStack, Consumer<Component>, Consumer<Component>> sourceTooltips) {
        ItemStack source = half.source();
        tooltip.accept(Component.empty()
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.translatable(sideKey).withStyle(ChatFormatting.BOLD))
                                .append(CommonComponents.SPACE)
                                .append(sourceName(source)));

        TooltipDisplay sourceDisplay = source.getOrDefault(
                DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (sourceDisplay.hideTooltip()) {
            return;
        }

        if (flag.isAdvanced()) {
            tooltip.accept(sourceId(source));
        }
        appendSourceDetails(source, context, hideGameplayComponents(sourceDisplay), flag,
                            sourceTooltips.apply(source, tooltip));
    }

    private static Component sourceId(ItemStack source) {
        return Component.literal(BuiltInRegistries.ITEM.getKey(source.getItem()).toString())
                        .withStyle(ChatFormatting.DARK_GRAY);
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
            TooltipFlag flag,
            Consumer<Component> tooltip) {
        if (!flag.isAdvanced()) {
            source.addDetailsToTooltip(context, display, null, flag, tooltip);
            return;
        }

        List<Component> details = new ArrayList<>();
        source.addDetailsToTooltip(context, display, null, flag, details::add);
        removeAdvancedStackDiagnostics(source, details);
        details.forEach(tooltip);
    }

    private static void removeAdvancedStackDiagnostics(ItemStack source, List<Component> details) {
        Component itemId = sourceId(source);
        int componentCount = source.getComponents().size();
        if (componentCount > 0) {
            Component countLine = Component.translatable("item.components", componentCount)
                                           .withStyle(ChatFormatting.DARK_GRAY);
            for (int index = details.size() - 2; index >= 0; index--) {
                if (details.get(index).equals(itemId) && details.get(index + 1).equals(countLine)) {
                    details.remove(index + 1);
                    details.remove(index);
                    return;
                }
            }
        }

        int itemIdIndex = details.lastIndexOf(itemId);
        if (itemIdIndex >= 0) {
            details.remove(itemIdIndex);
        }
    }
}
