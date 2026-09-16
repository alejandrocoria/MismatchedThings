package games.alejandrocoria.mismatchedboots.gametest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.gametest.ItemSubmissionPoseProbe;
import games.alejandrocoria.mismatchedboots.gametest.RuntimeItemSubmissionProbe;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubmitNodeStorage.class)
abstract class SubmitNodeStorageProbeMixin {
    @Inject(method = "submitItem", at = @At("HEAD"))
    private void mismatchedboots$recordItemPose(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int light,
            int overlay,
            int outlineColor,
            int[] tints,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType,
            CallbackInfo callback) {
        ItemSubmissionPoseProbe.record(displayContext, poseStack.last().pose(), quads);
        RuntimeItemSubmissionProbe.record(displayContext, poseStack.last().pose(), quads, foilType);
    }
}
