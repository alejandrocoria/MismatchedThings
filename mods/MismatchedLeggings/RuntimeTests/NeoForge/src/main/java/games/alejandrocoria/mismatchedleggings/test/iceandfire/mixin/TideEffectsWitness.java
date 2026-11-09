package games.alejandrocoria.mismatchedleggings.test.iceandfire.mixin;

import games.alejandrocoria.mismatchedleggings.test.iceandfire.TideChecks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class TideEffectsWitness {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"))
    private void tideTest$effect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> callback) {
        TideChecks.effect((LivingEntity) (Object) this, effect);
    }
}
