package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(JustDireEarthquakeCompatibility.class)
abstract class JustDireEarthquakeApiMissingMixin {
    @ModifyVariable(method = "component", at = @At("HEAD"), argsOnly = true)
    private static String missing(String path) { return "missing_earthquake_fixture_component"; }
}
