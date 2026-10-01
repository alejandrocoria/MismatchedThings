package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin;

import com.zurrtum.create.client.foundation.item.TooltipModifier;
import games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mixin(MixedBootsTooltipFormatter.class)
public abstract class MixedBootsTooltipFormatterMixin {
    private static final Component PLACEHOLDER =
            Component.literal("mismatchedboots-create-fly-tooltip-placeholder");

    @Inject(method = "appendSourceDetails", at = @At("HEAD"))
    private static void appendCreateFlyTooltip(
            ItemStack source,
            Item.TooltipContext context,
            TooltipDisplay display,
            TooltipFlag flag,
            Consumer<Component> tooltip,
            CallbackInfo callback) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        TooltipModifier modifier = TooltipModifier.REGISTRY.get(source.getItem());
        if (modifier == null) {
            return;
        }

        List<Component> contribution = new ArrayList<>();
        contribution.add(PLACEHOLDER);
        modifier.modify(contribution, player);
        int placeholderIndex = -1;
        for (int index = 0; index < contribution.size(); index++) {
            if (contribution.get(index) == PLACEHOLDER) {
                placeholderIndex = index;
                break;
            }
        }
        if (placeholderIndex < 0) {
            return;
        }
        contribution.remove(placeholderIndex);
        contribution.forEach(tooltip);
    }
}
