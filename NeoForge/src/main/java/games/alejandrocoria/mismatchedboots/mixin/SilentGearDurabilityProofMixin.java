package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearEffectReadiness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.DurabilityTraitEffect", remap = false)
abstract class SilentGearDurabilityProofMixin implements SilentGearEffectReadiness {
    @Unique public boolean mismatchedboots$sgWearReady() { return false; }
}
