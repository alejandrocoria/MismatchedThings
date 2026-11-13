package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import games.alejandrocoria.mismatchedleggings.test.DecoySpikeClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class DecoySpikeDrawMixin {
    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"))
    private void draw(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
                      int sourceWidth, int sourceHeight, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (DecoySpikeClient.capturing) {
            DecoySpikeClient.draws.add(new DecoySpikeClient.Draw(texture, x, y, u, v, width, height));
        }
    }
}
