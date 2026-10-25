package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Native APIs remain intact; only the optional adapter's required binding is unavailable. */
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireNegateFallCompatibility", remap = false)
abstract class JustDireNegateApiMissingMixin {
    @ModifyVariable(method = "component", at = @At("HEAD"), argsOnly = true)
    private static String missing(String path) {
        return "missing_negate_fixture_component";
    }
}
