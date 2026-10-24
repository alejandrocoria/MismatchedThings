package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Only the refund adapter's shared-metadata lookup fails; normal native installation queries stay real. */
@Mixin(JustDireUpgradesCompatibility.class)
abstract class JustDireRefundMetadataMissingMixin {
    @ModifyVariable(method = "installedFlag", at = @At("HEAD"), argsOnly = true)
    private static String missing(String name) { return "missing_refund_metadata"; }
}
