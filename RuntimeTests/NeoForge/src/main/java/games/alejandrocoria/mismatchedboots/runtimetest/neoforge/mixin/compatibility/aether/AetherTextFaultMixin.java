package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.client.compatibility.aether.AetherTooltipClient;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipTestControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AetherTooltipClient.class)
abstract class AetherTextFaultMixin {
    @Inject(method = "component", at = @At("RETURN"))
    private static void binding(String name, CallbackInfoReturnable<?> callback) {
        String variant = System.getProperty("mismatchedboots.runtimetest.aetherTooltipVariant");
        if (variant.equals("setbindingfailure") && name.equals("armor_set")
                || variant.equals("tierbindingfailure") && name.equals("reinforcement_tier")) {
            throw new NoSuchFieldError("Controlled native tooltip component binding failure: " + name);
        }
    }
    @Inject(method = "sourceLines", at = @At("RETURN"))
    private static void populated(CallbackInfoReturnable<?> callback) {
        if (System.getProperty("mismatchedboots.runtimetest.aetherTooltipVariant").equals("setpopulatedfailure")) {
            AetherTooltipTestControl.rejectedSet((java.util.List<?>) callback.getReturnValue());
            throw new IllegalStateException("Controlled populated set tooltip failure");
        }
    }
}
