package games.alejandrocoria.mismatchedleggings.test.silentgear.tooltip;

import games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Independent peer baseline only; Leggings' binding/formatter faults never target the Boots adapter. */
@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedboots.client.compatibility.silentgear.SilentGearTooltipClient", remap = false)
abstract class TooltipBootsBaselineMixin {
    @Inject(method = "appendSource", at = @At("HEAD"), cancellable = true)
    private static void baseline(ItemStack source, TooltipFlag flag, Consumer<Component> output, CallbackInfo callback) {
        if (TooltipClient.omitPresentation()) {
            callback.cancel();
        }
    }
}
