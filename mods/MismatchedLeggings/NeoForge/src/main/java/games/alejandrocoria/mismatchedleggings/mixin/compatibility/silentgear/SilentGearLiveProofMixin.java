package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearLiveReadiness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {
        "net.silentchaos512.gear.gear.trait.effect.OxidationTraitEffect",
        "net.silentchaos512.gear.gear.trait.effect.NumberPropertyModifierTraitEffect",
        "net.silentchaos512.gear.gear.trait.effect.NumberPropertyModifierTraitEffect$StatMod"
}, remap = false)
abstract class SilentGearLiveProofMixin implements SilentGearLiveReadiness {
    @Unique
    public boolean mismatchedleggings$sgLiveReady() { return false; }
}
