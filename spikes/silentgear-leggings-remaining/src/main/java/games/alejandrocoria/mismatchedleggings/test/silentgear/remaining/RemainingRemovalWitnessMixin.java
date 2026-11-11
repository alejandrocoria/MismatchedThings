package games.alejandrocoria.mismatchedleggings.test.silentgear.remaining;

import games.alejandrocoria.mismatchedleggings.test.silentgear.RemainingSpike;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class RemainingRemovalWitnessMixin {
    @Inject(method = "removeEffect", at = @At("HEAD"))
    private void witness(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> ci) {
        RemainingSpike.removal((LivingEntity) (Object) this, effect);
    }
}
