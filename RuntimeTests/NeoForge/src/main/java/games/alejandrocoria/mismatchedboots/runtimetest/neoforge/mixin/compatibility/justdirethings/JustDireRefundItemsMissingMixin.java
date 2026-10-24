package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(JustDireRefundCompatibility.class)
abstract class JustDireRefundItemsMissingMixin {
    @ModifyArg(method = "bindItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resources/Identifier;fromNamespaceAndPath(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"), index = 1)
    private static String missing(String path) { return path.equals("upgrade_earthquake") ? "missing_refund_fixture_item" : path; }
}
