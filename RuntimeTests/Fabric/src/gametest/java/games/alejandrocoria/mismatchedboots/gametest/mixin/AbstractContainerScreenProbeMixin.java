package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.GuiSpriteSubmissionProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenProbeMixin {
    @Inject(method = "extractSlots", at = @At("HEAD"))
    private void mismatchedboots$recordSlotFrame(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo callback) {
        GuiSpriteSubmissionProbe.recordFrame();
    }
}
