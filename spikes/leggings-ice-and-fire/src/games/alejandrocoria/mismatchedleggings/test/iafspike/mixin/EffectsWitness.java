package games.alejandrocoria.mismatchedleggings.test.iafspike.mixin;

import games.alejandrocoria.mismatchedleggings.test.iafspike.SpikeChecks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class EffectsWitness {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"))
    private void iafSpike$effect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> callback) {
        SpikeChecks.effect((LivingEntity) (Object) this, effect);
    }
}
