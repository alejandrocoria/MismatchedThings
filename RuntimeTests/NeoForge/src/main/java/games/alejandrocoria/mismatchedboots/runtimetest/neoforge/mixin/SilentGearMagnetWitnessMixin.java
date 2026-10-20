package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearMagnetSpike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect", remap = false)
abstract class SilentGearMagnetWitnessMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void count(CallbackInfo ci) { SilentGearMagnetSpike.nativeCall(); }
}
