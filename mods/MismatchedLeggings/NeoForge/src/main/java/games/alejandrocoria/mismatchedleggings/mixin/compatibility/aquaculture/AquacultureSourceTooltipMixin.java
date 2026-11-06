package games.alejandrocoria.mismatchedleggings.mixin.compatibility.aquaculture;

import games.alejandrocoria.mismatchedleggings.compatibility.aquaculture.AquacultureTooltipCompatibility;
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
abstract class AquacultureSourceTooltipMixin {
    @Inject(method = "appendSourceDetails", at = @At("HEAD"))
    private static void mismatchedleggings$aquacultureSource(
            ItemStack source,
            Item.TooltipContext context,
            TooltipDisplay display,
            TooltipFlag flag,
            Consumer<Component> tooltip,
            CallbackInfo callback) {
        AquacultureTooltipCompatibility.appendSource(source, flag, tooltip);
    }
}
