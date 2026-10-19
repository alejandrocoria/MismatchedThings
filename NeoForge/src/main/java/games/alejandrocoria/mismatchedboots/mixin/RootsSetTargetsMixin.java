package games.alejandrocoria.mismatchedboots.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Enroll optional set consumers in final export; their local predicates are checked/edited there. */
@Pseudo
@Mixin(targets = {"elucent.rootsclassic.item.StaffItem", "elucent.rootsclassic.item.CrystalStaffItem",
        "elucent.rootsclassic.event.ComponentSpellsEvent"}, remap = false)
abstract class RootsSetTargetsMixin {}
