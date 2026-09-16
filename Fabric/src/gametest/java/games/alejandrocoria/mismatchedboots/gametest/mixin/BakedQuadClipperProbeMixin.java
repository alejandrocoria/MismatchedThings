package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.client.render.geometry.BakedQuadClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.gametest.QuadPlanningProbe;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BakedQuadClipper.class, remap = false)
abstract class BakedQuadClipperProbeMixin {
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
