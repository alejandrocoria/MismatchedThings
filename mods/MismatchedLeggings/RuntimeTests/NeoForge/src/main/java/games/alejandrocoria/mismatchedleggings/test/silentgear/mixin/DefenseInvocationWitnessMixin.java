package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearDefenseApi;
import games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SilentGearDefenseApi.class, remap = false)
abstract class DefenseInvocationWitnessMixin {
    @Inject(method = "invoke", at = @At("HEAD"))
    private void before(CallbackInfoReturnable<Float> callback) {
        DefenseProbe.before();
    }
}
