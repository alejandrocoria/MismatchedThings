package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairTestSupport;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class JustDireLavaRepairEffectsMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void observe(ParticleOptions options, double x, double y, double z, int count, double dx, double dy, double dz, double speed,
                         CallbackInfoReturnable<Integer> result) {
        JustDireLavaRepairTestSupport.particle(options, x, y, z, count, dx, dy, dz, speed);
    }
}
