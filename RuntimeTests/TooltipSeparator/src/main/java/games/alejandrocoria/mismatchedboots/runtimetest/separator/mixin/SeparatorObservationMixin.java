package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.client.tooltip.TooltipSeparatorComponent;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipSeparatorComponent.class)
abstract class SeparatorObservationMixin {
    @Inject(method = "extractImage", at = @At("RETURN"))
    private void observe(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics, CallbackInfo callback) {
        if (SeparatorObservations.enabled()) {
            SeparatorObservations.WIDTHS.add(width);
            SeparatorObservations.DRAWS.add(new SeparatorObservations.Draw(true, y,
                                                                           ((TooltipSeparatorComponent) (Object) this).getHeight(font)));
        }
    }
}
