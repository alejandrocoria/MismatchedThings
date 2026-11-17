package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Gives the test plugin access to the relocated tier producer's final arguments. */
@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.client.AetherIIClientEventListeners", remap = false)
abstract class AetherTooltipClientPeerMixin {
}
