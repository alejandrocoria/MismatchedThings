package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Alter the actual readiness getter; final export must leave it denied, never republish permission. */
@Mixin(value = CommonHooks.class, priority = 500)
abstract class JustDireAttributeDeniedMixin {
    @ModifyReturnValue(method = "mismatchedboots$justDireAttributesReady", at = @At("RETURN"))
    private static boolean deny(boolean original) { return false; }
}
