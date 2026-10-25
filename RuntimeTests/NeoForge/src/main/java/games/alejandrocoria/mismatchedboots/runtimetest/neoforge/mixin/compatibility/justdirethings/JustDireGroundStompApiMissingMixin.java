package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(JustDireGroundStompCompatibility.class)
abstract class JustDireGroundStompApiMissingMixin {
    @ModifyVariable(method = "component", at = @At("HEAD"), argsOnly = true)
    private static String missing(String path) {
        return "missing_ground_stomp_fixture_component";
    }
}
