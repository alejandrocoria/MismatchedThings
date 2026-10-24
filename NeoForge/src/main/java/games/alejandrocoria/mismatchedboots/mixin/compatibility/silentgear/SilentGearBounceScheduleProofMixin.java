package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.lib.event.ServerTicks", remap = false)
abstract class SilentGearBounceScheduleProofMixin {
    @Unique private static boolean mismatchedboots$sgScheduleReady() { return false; }
}
