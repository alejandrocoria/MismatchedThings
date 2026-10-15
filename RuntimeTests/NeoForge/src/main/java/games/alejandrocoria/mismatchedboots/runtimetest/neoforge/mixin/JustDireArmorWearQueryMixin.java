package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireArmorWearFaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JustDireResourcesCompatibility.class)
abstract class JustDireArmorWearQueryMixin {
    @Inject(method = "wearProfile", at = @At("HEAD"))
    private static void fail(CallbackInfoReturnable<?> callback) {
        if (JustDireArmorWearFaults.fault.equals("query")) { throw new IllegalStateException("isolated wear query failure"); }
    }
}

