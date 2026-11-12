package games.alejandrocoria.mismatchedleggings.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireTooltipCompatibility;
import games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(MixedLeggingsTooltipFormatter.class)
abstract class JustDireSourceTooltipMixin {
    @ModifyVariable(method = "appendSourceDetails", at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> mismatchedleggings$historicalState(Consumer<Component> output,
            ItemStack source, Item.TooltipContext context, TooltipDisplay display, TooltipFlag flag, Consumer<Component> original) {
        return JustDireTooltipCompatibility.sourceTooltip(source, output);
    }
}
