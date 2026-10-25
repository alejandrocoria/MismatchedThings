package games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.util.TraitHelper", remap = false)
abstract class SilentGearWearerQueryProofMixin {
    @Unique
    private static boolean mismatchedboots$sgWearerQueryReady() {
        return false;
    }
}
