package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(JustDireSettingsCompatibility.class)
abstract class JustDireSettingsApiMissingMixin {
    @ModifyVariable(method = "component", at = @At("HEAD"), argsOnly = true)
    private static String fail(String path) { return "missing_mixed_settings_fixture_component"; }
}
