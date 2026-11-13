package games.alejandrocoria.mismatchedleggings.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The fixture plugin changes only the Leggings view's slot argument after production hooks apply. */
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", priority = 500, remap = false)
abstract class JustDireHudFaultProbe {
    @Unique
    private static void leggings$hudFaultMarker() {}
}
