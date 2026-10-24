package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeServerGuardPlugin;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class JustDireEarthquakeFeedbackMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void particles(ParticleOptions options, double x, double y, double z, int count, double dx, double dy, double dz, double speed, CallbackInfoReturnable<Integer> ci) {
        if (options.getType() == ParticleTypes.END_ROD || options.getType() == ParticleTypes.ENCHANT) {
            if (count != 20 || dy != .2 || speed != 0) { throw new AssertionError("Wrong Earthquake particles"); }
            JustDireEarthquakeServerGuardPlugin.particles++;
        }
    }
}
