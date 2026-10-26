package games.alejandrocoria.mismatchedboots.mixin.compatibility.rootsclassic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "elucent.rootsclassic.util.RootsUtil", remap = false)
abstract class RootsRepairProofMixin {
    private static boolean mismatchedboots$rootsRepairReady() {
        return false;
    }
}
