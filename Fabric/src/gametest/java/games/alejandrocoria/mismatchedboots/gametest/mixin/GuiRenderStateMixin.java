package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.GuiItemSubmissionProbe;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
abstract class GuiRenderStateMixin {
    @Inject(method = "addItem", at = @At("HEAD"))
    private void mismatchedboots$recordItem(GuiItemRenderState state, CallbackInfo callback) {
        GuiItemSubmissionProbe.record(state);
    }
}
