package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffectReadiness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.DurabilityTraitEffect", remap = false)
abstract class SilentGearDurabilityProofMixin implements SilentGearEffectReadiness {
    @Unique
    public boolean mismatchedleggings$sgWearReady() {
        return false;
    }
}
