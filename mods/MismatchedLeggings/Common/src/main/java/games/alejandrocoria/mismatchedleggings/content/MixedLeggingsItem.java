package games.alejandrocoria.mismatchedleggings.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class MixedLeggingsItem extends Item {
    public MixedLeggingsItem(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                               Consumer<Component> tooltip, TooltipFlag flag) {
        // Block A registers only the inert item, not a source schema or composition recipe.
        tooltip.accept(Component.translatable("tooltip.mismatchedleggings.mixed_leggings.invalid")
                .withStyle(ChatFormatting.RED));
    }
}
