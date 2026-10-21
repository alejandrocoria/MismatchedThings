package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.client.compatibility.silentgear.SilentGearTooltipClient;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTooltipClientSpike;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Suppress only the new block for independent baseline capture; never append a second renderer. */
@Mixin(SilentGearTooltipClient.class)
abstract class SilentGearTooltipProductionControlMixin {
    @Inject(method = "appendSource", at = @At("HEAD"), cancellable = true)
    private static void baseline(ItemStack source, TooltipFlag flag, Consumer<Component> output, CallbackInfo callback) {
        if (SilentGearTooltipClientSpike.omitPresentation()) { callback.cancel(); }
    }
}
