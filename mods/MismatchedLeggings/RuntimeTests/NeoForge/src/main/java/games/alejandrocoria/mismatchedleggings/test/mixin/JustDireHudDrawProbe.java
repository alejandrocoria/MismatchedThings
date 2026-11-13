package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import games.alejandrocoria.mismatchedleggings.test.JustDireHudClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class JustDireHudDrawProbe {
    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"), require = 1)
    private void draw(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                      int sourceWidth, int sourceHeight, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (JustDireHudClient.capturing) {
            JustDireHudClient.draws.add(new JustDireHudClient.Draw(texture, x, y, u, v, width, height));
        }
    }
}
