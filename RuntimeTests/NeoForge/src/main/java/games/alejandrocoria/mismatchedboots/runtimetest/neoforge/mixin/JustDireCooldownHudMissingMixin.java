package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Late target visit for a discarded production result, not an alternate read view. */
@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", priority = 500, remap = false)
abstract class JustDireCooldownHudMissingMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void fixtureLateVisit(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {}
}
