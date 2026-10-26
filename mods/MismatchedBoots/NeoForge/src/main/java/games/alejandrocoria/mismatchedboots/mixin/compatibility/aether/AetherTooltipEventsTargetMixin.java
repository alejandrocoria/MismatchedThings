package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The plugin guards only the two final native presentation call sites. */
@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.AetherIIEventListeners", remap = false)
abstract class AetherTooltipEventsTargetMixin {
}
