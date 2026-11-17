package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Tier presentation moved to the native client event subscriber in alpha.4.2. */
@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.client.AetherIIClientEventListeners", remap = false)
abstract class AetherTooltipClientEventsTargetMixin {
}
