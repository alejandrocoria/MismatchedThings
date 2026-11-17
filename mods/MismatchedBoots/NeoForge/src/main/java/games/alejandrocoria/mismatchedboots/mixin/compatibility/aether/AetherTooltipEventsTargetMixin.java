package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The client-only plugin guards the relocated common attribute producer. */
@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.AetherIIEventListeners", remap = false)
abstract class AetherTooltipEventsTargetMixin {
}
