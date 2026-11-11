package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearTooltipCompatibility;
import games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(MixedLeggingsTooltipFormatter.class)
abstract class SilentGearSourceTooltipMixin {
    @Inject(method = "appendSourceDetails", at = @At("HEAD"))
    private static void mismatchedleggings$silentGearSource(
            ItemStack source,
            Item.TooltipContext context,
            TooltipDisplay display,
            TooltipFlag flag,
            Consumer<Component> tooltip,
            CallbackInfo callback) {
        SilentGearTooltipCompatibility.appendSource(source, flag, tooltip);
    }
}
