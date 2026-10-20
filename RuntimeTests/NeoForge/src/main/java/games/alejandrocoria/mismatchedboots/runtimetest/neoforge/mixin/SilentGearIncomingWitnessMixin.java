package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseApi;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearIncomingTest;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes/throws after the actual guarded native leaf, without altering its final code proof. */
@Mixin(value = SilentGearDefenseApi.class, remap = false)
abstract class SilentGearIncomingWitnessMixin {
    @Inject(method = "incoming", at = @At("RETURN"))
    private void sgtest$afterNative(Object instance, Object effect, ItemStack context, LivingEntity actor, DamageSource source,
            float current, float base, CallbackInfoReturnable<Float> callback) {
        SilentGearIncomingTest.called();
    }
}
