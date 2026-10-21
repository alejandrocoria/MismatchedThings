package games.alejandrocoria.mismatchedboots.content;

import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class MixedBootsItem extends Item {
    public MixedBootsItem(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag) {
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.mismatchedboots.mixed_boots.invalid")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        MixedBootsTooltipFormatter.appendSourceSections(data, context, flag, tooltip,
                (source, output) -> sourceTooltip(source, flag, output));
    }

    /** Optional item-local adaptation of source information; stored sources remain read-only provenance. */
    protected Consumer<Component> sourceTooltip(ItemStack source, TooltipFlag flag, Consumer<Component> tooltip) {
        return tooltip;
    }
}
