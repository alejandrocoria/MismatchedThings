package games.alejandrocoria.mismatchedboots.content;

import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
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

        tooltip.accept(Component.translatable(
                        "tooltip.mismatchedboots.mixed_boots.left", data.left().source().getHoverName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(
                        "tooltip.mismatchedboots.mixed_boots.right", data.right().source().getHoverName())
                .withStyle(ChatFormatting.GRAY));
    }
}
