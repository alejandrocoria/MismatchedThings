package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for a local optional-binding fault applied after normal compilation. */
@Mixin(value = JustDireLavaRepairCompatibility.class, remap = false)
public abstract class JustDireLavaRepairApiMixin {}
