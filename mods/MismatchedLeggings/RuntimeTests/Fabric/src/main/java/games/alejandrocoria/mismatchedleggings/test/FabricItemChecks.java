package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.mixin.client.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedleggings.mixin.client.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.ItemClient;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Controlled FRAPI-only sources: actual API emission, not a production renderer switch. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricItemChecks {
    public static boolean meshMode;

    private FabricItemChecks() {}

    public static void install() {
        ItemChecks.platformFaces = layer -> {
            var mesh = ((LayerRenderStateExtension) (Object) layer).fabric_getMutableMesh();
            if (mesh == null) { return List.of(); }
            var result = new ArrayList<ItemChecks.Face>();
            var fields = (LayerRenderStateAccessor) (Object) layer;
            mesh.forEach(quad -> {
                var vertices = new ArrayList<Vector3f>();
                for (int i = 0; i < 4; i++) {
                    vertices.add(fields.mismatchedleggings$getLocalTransform().transformPosition(quad.copyPos(i, null)));
                }
                var foil = quad.foilType() == null ? fields.mismatchedleggings$getFoilType() : quad.foilType();
                result.add(new ItemChecks.Face(quad.tag() == 1 ? "redstone_block" : "lapis_block", vertices,
                        foil != ItemStackRenderState.FoilType.NONE));
            });
            return result;
        };
        ItemClient.platformChecks = () -> {
            meshMode = true;
            try {
                ItemChecks.run(Minecraft.getInstance());
            } finally {
                meshMode = false;
            }
        };
    }

    public static void convert(ItemStackRenderState state, Identifier model) {
        if (!meshMode || model == null || !model.getNamespace().equals("leggings_test")
                || !(model.getPath().equals("red") || model.getPath().equals("blue"))) { return; }
        var fields = (ItemStackRenderStateAccessor) (Object) state;
        var layer = fields.mismatchedleggings$getLayers()[fields.mismatchedleggings$getActiveLayerCount() - 1];
        var quads = ((LayerRenderStateAccessor) (Object) layer).mismatchedleggings$getQuads();
        var emitter = ((FabricLayerRenderState) (Object) layer).emitter();
        for (var quad : quads) {
            emitter.fromBakedQuad(quad).tag(model.getPath().equals("red") ? 1 : 2).emit();
        }
        quads.clear();
    }
}
