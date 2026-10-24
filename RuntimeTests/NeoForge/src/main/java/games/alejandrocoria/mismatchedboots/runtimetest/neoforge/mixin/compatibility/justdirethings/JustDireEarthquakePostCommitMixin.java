package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeServerGuardPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JustDireEarthquakeCompatibility.class)
abstract class JustDireEarthquakePostCommitMixin {
    @Inject(method = "activate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", shift = At.Shift.AFTER))
    private static void fail(CallbackInfoReturnable<Boolean> callback) {
        if (JustDireEarthquakeServerGuardPlugin.fault == 4) { throw new JustDireEarthquakeServerGuardPlugin.PostCommitFailure(); }
    }
}
