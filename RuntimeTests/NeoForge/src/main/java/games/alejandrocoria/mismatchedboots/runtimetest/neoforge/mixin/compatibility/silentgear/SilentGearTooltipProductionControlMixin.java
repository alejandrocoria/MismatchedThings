package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.client.compatibility.silentgear.SilentGearTooltipClient;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTooltipClientTest;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** Independent baseline suppression and a controlled binding fault; never append a second renderer. */
@Mixin(SilentGearTooltipClient.class)
abstract class SilentGearTooltipProductionControlMixin {
    @Inject(method = "appendSource", at = @At("HEAD"), cancellable = true)
    private static void baseline(ItemStack source, TooltipFlag flag, Consumer<Component> output, CallbackInfo callback) {
        if (SilentGearTooltipClientTest.omitPresentation()) { callback.cancel(); }
    }

    @Inject(method = "nativeClass", at = @At("HEAD"))
    private static void binding(String name, CallbackInfoReturnable<Class<?>> callback) throws ClassNotFoundException {
        SilentGearTooltipClientTest.bindingFault();
    }
}
