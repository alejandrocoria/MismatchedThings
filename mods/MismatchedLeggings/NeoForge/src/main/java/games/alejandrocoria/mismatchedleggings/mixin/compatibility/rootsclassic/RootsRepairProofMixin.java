package games.alejandrocoria.mismatchedleggings.mixin.compatibility.rootsclassic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "elucent.rootsclassic.util.RootsUtil", remap = false)
abstract class RootsRepairProofMixin {
    private static boolean mismatchedleggings$rootsRepairReady() {
        return false;
    }
}
