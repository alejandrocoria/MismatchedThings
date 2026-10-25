package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.util.MagnetPullTracker", remap = false)
abstract class SilentGearMagnetQueueProofMixin {
    @Unique
    private static boolean mismatchedboots$sgMagnetQueueReady() {
        return false;
    }
}
