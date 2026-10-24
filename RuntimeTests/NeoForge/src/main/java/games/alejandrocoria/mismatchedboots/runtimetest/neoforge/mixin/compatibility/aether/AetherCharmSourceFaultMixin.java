package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.client.compatibility.aether.AetherSourceCharms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Production has already collected detached holders/names, but has not published the optional source group. */
@Mixin(AetherSourceCharms.class)
abstract class AetherCharmSourceFaultMixin {
    @Inject(method = "marker", at = @At("RETURN"))
    private static void fault(CallbackInfoReturnable<?> callback) {
        if (callback.getReturnValue() != null) { throw new IllegalStateException("Controlled populated source charm formatter failure"); }
    }
}
