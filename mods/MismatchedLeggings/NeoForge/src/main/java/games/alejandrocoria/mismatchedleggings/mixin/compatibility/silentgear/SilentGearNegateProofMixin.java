package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.NegateDamageTraitEffect", remap = false)
abstract class SilentGearNegateProofMixin {
    @Unique
    private static boolean mismatchedleggings$sgNegateReady() {
        return false;
    }
}
