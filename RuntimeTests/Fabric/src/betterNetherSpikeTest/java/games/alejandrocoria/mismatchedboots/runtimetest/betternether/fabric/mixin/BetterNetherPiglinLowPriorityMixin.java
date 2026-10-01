package games.alejandrocoria.mismatchedboots.runtimetest.betternether.fabric.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.betternether.fabric.BetterNetherPiglinArmorAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PiglinAi.class, priority = 900)
abstract class BetterNetherPiglinLowPriorityMixin {
    @Inject(method = "isWearingSafeArmor", at = @At("RETURN"), cancellable = true)
    private static void mismatchedboots$augmentLow(
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> callback) {
        BetterNetherPiglinArmorAdapter.augment(entity, callback, "low");
    }
}
