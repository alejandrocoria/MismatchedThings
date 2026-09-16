package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.client.render.geometry.FabricMeshClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.LayerExtensionMetadata;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class LayerRenderStateExtensionMixin implements LayerExtensionMetadata {
    @Override
    public Plan mismatchedboots$planGeometry(Matrix4fc localTransform, QuadClipper.Side side) {
        MutableMesh mesh = ((LayerRenderStateExtension) (Object) this).fabric_getMutableMesh();
        return FabricMeshClipper.plan(mesh, localTransform, side);
    }
}
