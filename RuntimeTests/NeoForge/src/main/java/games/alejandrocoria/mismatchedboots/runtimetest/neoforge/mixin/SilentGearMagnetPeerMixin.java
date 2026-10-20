package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = {"net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect", "net.silentchaos512.gear.gear.util.MagnetPullTracker"}, remap = false)
abstract class SilentGearMagnetPeerMixin {
    @Unique private static boolean sgtest$unrelatedMagnetMethod() { return true; }
}
