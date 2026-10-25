package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipLifecycleTest;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Move only the hidden fixture's GUI coordinates; normal native rendering performs hit-testing and tooltip creation. */
@Mixin(MouseHandler.class)
abstract class AetherTooltipMouseMixin {
    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void x(CallbackInfoReturnable<Double> callback) {
        Double value = AetherTooltipLifecycleTest.mouse(true);
        if (value != null) {
            callback.setReturnValue(value);
        }
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void y(CallbackInfoReturnable<Double> callback) {
        Double value = AetherTooltipLifecycleTest.mouse(false);
        if (value != null) {
            callback.setReturnValue(value);
        }
    }
}
