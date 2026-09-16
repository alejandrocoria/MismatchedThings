package games.alejandrocoria.mismatchedboots.gametest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.gametest.ItemSubmissionPoseProbe;
import games.alejandrocoria.mismatchedboots.gametest.MeshSubmissionProbe;
import games.alejandrocoria.mismatchedboots.gametest.RuntimeItemSubmissionProbe;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = SubmitNodeStorage.class, priority = 900)
abstract class SubmitNodeStorageProbeMixin {
    @Inject(method = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", at = @At("HEAD"))
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
        MeshSubmissionProbe.record(displayContext, poseStack.last().pose(), tints, quads, null, foilType);
    }

    @Inject(method = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MeshView;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V",
            at = @At("HEAD"), remap = false)
    private void recordMesh(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                            int[] tints, List<BakedQuad> quads,
                            MeshView mesh,
                            ItemStackRenderState.FoilType foil, CallbackInfo callback) {
        RuntimeItemSubmissionProbe.record(context, pose.last().pose(), quads, foil);
        RuntimeItemSubmissionProbe.recordMesh(context, pose.last().pose(), mesh, foil);
        MeshSubmissionProbe.record(
                context, pose.last().pose(), tints, quads, mesh, foil);
    }
}
