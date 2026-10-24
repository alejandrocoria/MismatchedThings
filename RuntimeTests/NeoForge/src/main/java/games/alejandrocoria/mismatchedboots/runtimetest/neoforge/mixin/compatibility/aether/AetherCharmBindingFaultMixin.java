package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.client.compatibility.aether.AetherSourceCharms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AetherSourceCharms.class)
abstract class AetherCharmBindingFaultMixin {
    @ModifyArg(method = "gather", at = @At(value = "INVOKE", target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"), index = 0)
    private static String missingFactoryType(String original) { return "mismatchedboots.fixture.MissingCharmTooltip"; }
}
