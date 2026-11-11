package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.CureChecks;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class CureRemovalWitnessMixin {
    @Inject(method = "removeEffect", at = @At("HEAD"), require = 1)
    private void count(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> ci) {
        CureChecks.removal((LivingEntity) (Object) this, effect);
    }
}
