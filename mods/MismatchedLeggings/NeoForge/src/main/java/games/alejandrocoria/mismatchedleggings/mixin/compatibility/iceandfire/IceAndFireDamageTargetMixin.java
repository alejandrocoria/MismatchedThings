package games.alejandrocoria.mismatchedleggings.mixin.compatibility.iceandfire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Enroll the optional handler for final, independently guarded LEGS edits. */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false)
abstract class IceAndFireDamageTargetMixin {}
