package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.common.items.interfaces.Ability", remap = false)
abstract class JustDireEarthquakeQueryFailureMixin {
    @Inject(method = "getFeCost", at = @At("HEAD"))
    private void fail(CallbackInfoReturnable<Integer> ci) {
        if (((Enum<?>) (Object) this).name().equals("EARTHQUAKE")) { throw new IllegalStateException("Earthquake read-only query fixture failure"); }
    }
}
