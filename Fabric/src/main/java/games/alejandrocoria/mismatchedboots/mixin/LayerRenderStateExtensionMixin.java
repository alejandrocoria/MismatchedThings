package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.client.render.geometry.LayerExtensionMetadata;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class LayerRenderStateExtensionMixin implements LayerExtensionMetadata {
    @Override
    public boolean mismatchedboots$hasUnsupportedGeometry() {
        MutableMesh mesh = ((LayerRenderStateExtension) (Object) this).fabric_getMutableMesh();
        return mesh != null && mesh.size() != 0;
    }
}
