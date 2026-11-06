package games.alejandrocoria.mismatchedleggings.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;

/** Simulate the reflection envelope of a fatal native failure without exhausting the VM. */
@Mixin(targets = "games.alejandrocoria.mismatchedthings.internal.compatibility.forbiddenarcanus.NeoForgeForbiddenModifierService$NativeApi", remap = false)
abstract class ForbiddenBootsNativeFailure {
    @Inject(method = "apply", at = @At("HEAD"))
    private void leggings$fatalNativeFailure(CallbackInfo callback) throws InvocationTargetException {
        if (Boolean.getBoolean("leggings.test.fatalForbiddenAdoption")) {
            throw new InvocationTargetException(new AssertionError("Expected fatal native adoption failure"));
        }
    }
}
