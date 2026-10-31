package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.gametest.HybridItemRenderingRuntimeClientGameTest;
import games.alejandrocoria.mismatchedboots.gametest.QuadPlanningProbe;
import games.alejandrocoria.mismatchedthings.api.client.geometry.BakedQuadClipper;
import games.alejandrocoria.mismatchedthings.api.client.geometry.QuadClipper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BakedQuadClipper.class, remap = false)
abstract class BakedQuadClipperProbeMixin {
    /** Declares unsupported input for the opt-in visual probe; production still owns the entire fallback. */
    @ModifyVariable(method = "clip", at = @At("HEAD"), argsOnly = true)
    private static BakedQuadClipper.ExtensionMetadata mismatchedboots$forceVisualFallback(
            BakedQuadClipper.ExtensionMetadata metadata) {
        return HybridItemRenderingRuntimeClientGameTest.forceVisualFallback
               ? BakedQuadClipper.ExtensionMetadata.UNSUPPORTED : metadata;
    }

    @Inject(method = "clip", at = @At("HEAD"))
    private static void mismatchedboots$recordPlannedQuad(
            BakedQuad quad,
            Matrix4fc localTransform,
            QuadClipper.Side side,
            BakedQuadClipper.ExtensionMetadata extensionMetadata,
            CallbackInfoReturnable<BakedQuadClipper.Result> callback) {
        QuadPlanningProbe.record();
    }
}
