package games.alejandrocoria.mismatchedleggings.test.mixin;

import games.alejandrocoria.mismatchedleggings.test.ForbiddenModifierPresentation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
abstract class ForbiddenTooltipProbe {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"))
    private static void leggings$background(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                           Identifier texture, CallbackInfo callback) {
        ForbiddenModifierPresentation.background(texture);
    }
}
