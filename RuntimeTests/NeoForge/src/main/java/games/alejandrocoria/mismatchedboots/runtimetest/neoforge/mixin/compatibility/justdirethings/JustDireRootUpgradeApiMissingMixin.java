package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Break only the adapter's installed-component lookup; registered native components remain intact. */
@Mixin(JustDireUpgradesCompatibility.class)
abstract class JustDireRootUpgradeApiMissingMixin {
    @ModifyVariable(method = "component", at = @At("HEAD"), argsOnly = true)
    private static String missing(String path) {
        return "missing_root_upgrade_fixture_component";
    }
}
