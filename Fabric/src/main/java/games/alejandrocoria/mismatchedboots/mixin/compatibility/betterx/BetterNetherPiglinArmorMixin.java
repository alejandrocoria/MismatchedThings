package games.alejandrocoria.mismatchedboots.mixin.compatibility.betterx;

import games.alejandrocoria.mismatchedboots.compatibility.betterx.BetterNetherPiglinArmorAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PiglinAi.class, priority = 900)
abstract class BetterNetherPiglinArmorMixin {
    @Inject(method = "isWearingSafeArmor", at = @At("RETURN"), cancellable = true)
    private static void mismatchedboots$augmentBetterNetherArmor(
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> callback) {
        BetterNetherPiglinArmorAdapter.augment(entity, callback);
    }
}
