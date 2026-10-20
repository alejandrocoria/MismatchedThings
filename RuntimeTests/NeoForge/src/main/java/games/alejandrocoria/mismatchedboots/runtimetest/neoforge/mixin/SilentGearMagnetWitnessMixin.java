package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnetApi;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearMagnetSpike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SilentGearMagnetApi.class, remap = false)
abstract class SilentGearMagnetWitnessMixin {
    @Inject(method = "invoke", at = @At("HEAD"))
    private void count(CallbackInfo ci) { SilentGearMagnetSpike.nativeCall(); }
}
