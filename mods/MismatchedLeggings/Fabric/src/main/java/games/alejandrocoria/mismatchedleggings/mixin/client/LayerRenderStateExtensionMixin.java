package games.alejandrocoria.mismatchedleggings.mixin.client;

import games.alejandrocoria.mismatchedleggings.client.render.geometry.FabricMeshClipper;
import games.alejandrocoria.mismatchedleggings.client.render.geometry.LayerGeometryExtension;
import games.alejandrocoria.mismatchedthings.api.client.geometry.QuadClipper;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class LayerRenderStateExtensionMixin implements LayerGeometryExtension {
    @Override
    public Plan mismatchedleggings$planGeometry(Matrix4fc localTransform, QuadClipper.Side side) {
        MutableMesh mesh = ((LayerRenderStateExtension) (Object) this).fabric_getMutableMesh();
        return FabricMeshClipper.plan(mesh, localTransform, side);
    }
}
