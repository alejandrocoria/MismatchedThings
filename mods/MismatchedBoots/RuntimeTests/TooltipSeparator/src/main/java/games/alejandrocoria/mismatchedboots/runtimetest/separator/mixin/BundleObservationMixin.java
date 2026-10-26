package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe a real foreign/native graphical row without replacing its layout or drawing. */
@Mixin(ClientBundleTooltip.class)
abstract class BundleObservationMixin {
    @Inject(method = "extractImage", at = @At("RETURN"))
    private void observe(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics, CallbackInfo callback) {
        if (SeparatorObservations.enabled()) {
            SeparatorObservations.DRAWS.add(new SeparatorObservations.Draw(false, y,
                                                                           ((ClientBundleTooltip) (Object) this).getHeight(font)));
        }
    }
}
