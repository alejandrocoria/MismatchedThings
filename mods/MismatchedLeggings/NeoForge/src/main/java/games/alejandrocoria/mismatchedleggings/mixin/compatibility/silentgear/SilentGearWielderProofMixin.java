package games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {
        "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect$PotionData",
        "net.silentchaos512.gear.util.TraitHelper",
        "net.silentchaos512.gear.util.GearHelper"
}, remap = false)
abstract class SilentGearWielderProofMixin {
    @Unique
    private static boolean mismatchedleggings$sgWielderReady() { return false; }
}
