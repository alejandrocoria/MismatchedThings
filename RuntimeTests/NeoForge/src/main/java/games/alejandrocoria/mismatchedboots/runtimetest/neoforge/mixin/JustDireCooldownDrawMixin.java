package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireCooldownFeedbackSpike;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class JustDireCooldownDrawMixin {
    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"))
    private void fixtureDraw(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
                             int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (JustDireCooldownFeedbackSpike.capturingDraws) {
            JustDireCooldownFeedbackSpike.draws.add(new JustDireCooldownFeedbackSpike.Draw(texture, x, y, u, v, width, height,
                    srcWidth, srcHeight, textureWidth, textureHeight, color));
        }
    }
}
