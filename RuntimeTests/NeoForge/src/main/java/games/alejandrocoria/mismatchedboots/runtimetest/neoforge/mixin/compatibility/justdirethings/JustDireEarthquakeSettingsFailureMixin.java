package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** A future variable native range must not expose an unused mixed slider or disable older consumers. */
@Mixin(JustDireSettingsCompatibility.class)
abstract class JustDireEarthquakeSettingsFailureMixin {
    @ModifyArg(method = "earthquakeProfile", at = @At(value = "INVOKE",
            target = "Lgames/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireSettingsCompatibility;nativeMetadata(Lgames/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireSettingsCompatibility$Api;Ljava/lang/Object;Z)Lgames/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireSettingsCompatibility$Metadata;"), index = 1)
    private static Object unsupported(Object actual) {
        try {
            return actual.getClass().getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
                         .newInstance(1, 2, 1, 1, 200, 800);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
