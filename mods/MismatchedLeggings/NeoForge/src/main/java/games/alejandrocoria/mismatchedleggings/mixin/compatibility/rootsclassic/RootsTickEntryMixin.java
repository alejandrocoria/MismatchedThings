package games.alejandrocoria.mismatchedleggings.mixin.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedleggings.compatibility.rootsclassic.RootsTickReadiness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"elucent.rootsclassic.item.SylvanArmorItem", "elucent.rootsclassic.item.WildwoodArmorItem"}, remap = false)
abstract class RootsTickEntryMixin implements RootsTickReadiness {
    @Override
    public boolean mismatchedleggings$rootsTickReady() {
        return false;
    }
}
