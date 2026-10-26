package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairTestSupport;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class JustDireLavaRepairParticleMixin {
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void observe(ParticleOptions options,
                         double x,
                         double y,
                         double z,
                         double dx,
                         double dy,
                         double dz,
                         CallbackInfoReturnable<Particle> result) {
        if (JustDireLavaRepairTestSupport.capture && options.getClass().getName().endsWith(".ItemFlowParticleData")) {
            if (result.getReturnValue() == null || !result.getReturnValue().getClass().getName().endsWith(".ItemFlowParticle")) {
                throw new AssertionError("Native JDT client particle factory was not used");
            }
            JustDireLavaRepairTestSupport.clientParticles++;
        }
    }
}
