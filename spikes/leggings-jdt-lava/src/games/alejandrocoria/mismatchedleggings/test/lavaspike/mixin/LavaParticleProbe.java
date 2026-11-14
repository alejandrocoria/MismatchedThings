package games.alejandrocoria.mismatchedleggings.test.lavaspike.mixin;

import games.alejandrocoria.mismatchedleggings.test.lavaspike.LavaClient;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

@Mixin(ParticleEngine.class)
public abstract class LavaParticleProbe {
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void observe(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                         CallbackInfoReturnable<Particle> result) {
        if (options.getClass().getName().endsWith(".ItemFlowParticleData")) {
            require(result.getReturnValue() != null && result.getReturnValue().getClass().getName().endsWith(".ItemFlowParticle"),
                    "Native JDT particle factory not used");
            LavaClient.particles++;
        }
    }
}
