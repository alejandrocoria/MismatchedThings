package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect", remap = false)
abstract class SilentGearMagnetProofMixin {
    @Unique private static boolean mismatchedboots$sgMagnetReady() { return false; }
}
