package games.alejandrocoria.mismatchedleggings.test.silentgear.tooltip;

import games.alejandrocoria.mismatchedleggings.client.compatibility.silentgear.SilentGearTooltipClient;
import games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Suppress only the optional production block while capturing its independent baseline. */
@Mixin(SilentGearTooltipClient.class)
abstract class TooltipBaselineMixin {
    @Inject(method = "appendSource", at = @At("HEAD"), cancellable = true)
    private static void baseline(ItemStack source, TooltipFlag flag, Consumer<Component> output, CallbackInfo callback) {
        if (TooltipClient.omitPresentation()) {
            callback.cancel();
        }
    }

    @Inject(method = "nativeClass", at = @At("HEAD"))
    private static void binding(String name, CallbackInfoReturnable<Class<?>> callback) throws ClassNotFoundException {
        TooltipClient.bindingFault();
    }
}
