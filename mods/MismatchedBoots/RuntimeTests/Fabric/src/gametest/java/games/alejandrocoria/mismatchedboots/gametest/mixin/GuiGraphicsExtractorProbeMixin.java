package games.alejandrocoria.mismatchedboots.gametest.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import games.alejandrocoria.mismatchedboots.gametest.GuiItemSubmissionProbe;
import games.alejandrocoria.mismatchedboots.gametest.GuiSpriteSubmissionProbe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorProbeMixin {
    @Inject(
            method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIIII)V",
            at = @At("HEAD"))
    private void mismatchedboots$recordSprite(
            RenderPipeline pipeline,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            int color,
            CallbackInfo callback) {
        GuiSpriteSubmissionProbe.record(sprite, x, y, width, height);
    }

    @Inject(
            method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;"
                    + "IILjava/lang/String;)V",
            at = @At("HEAD"))
    private void mismatchedboots$recordItemDecoration(
            Font font,
            ItemStack stack,
            int x,
            int y,
            String countText,
            CallbackInfo callback) {
        GuiItemSubmissionProbe.recordDecoration(stack, x, y, countText);
    }
}
