package games.alejandrocoria.mismatchedboots.gametest.mixin;

import games.alejandrocoria.mismatchedboots.client.render.geometry.FabricMeshClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.gametest.QuadPlanningProbe;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FabricMeshClipper.class, remap = false)
abstract class FabricMeshClipperProbeMixin {
    @Inject(method = "clip", at = @At("HEAD"))
    private static void recordFace(QuadView quad, Matrix4fc transform, QuadClipper.Side side,
                                   CallbackInfoReturnable<QuadClipper.TracedPolygonResult> callback) {
        QuadPlanningProbe.record();
    }
}
