package games.alejandrocoria.mismatchedthings.test.mixin;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchBlock;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchContent;
import games.alejandrocoria.mismatchedthings.test.WorkbenchCoexistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts real registration initialization without adding test hooks to a released core. */
@Mixin(value = WorkbenchContent.class, remap = false)
public abstract class CoreRegistrationProbe {
    @Inject(method = "createBlock", at = @At("HEAD"), require = 1)
    private static void countRegistration(CallbackInfoReturnable<WorkbenchBlock> callback) {
        WorkbenchCoexistence.REGISTRATIONS.incrementAndGet();
    }
}
