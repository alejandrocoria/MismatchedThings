package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireTooltipTestSupport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Controlled Shift only during tooltip capture in the hidden fixture, never production or ordinary input. */
@Mixin(Minecraft.class)
abstract class JustDireTooltipShiftMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private void tooltipShift(CallbackInfoReturnable<Boolean> callback) {
        Boolean shift = JustDireTooltipTestSupport.shift();
        if (shift != null) { callback.setReturnValue(shift); }
    }
}
