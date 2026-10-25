package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricLayerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/** Test-only emission through the real API, without a dependency on an external mod. */
public final class MeshTestFixtures {
    public static boolean runtimeRoute;
    public static final Map<Identifier, Mode> models = new java.util.HashMap<>();
    private static final List<String> SPRITES = List.of("red_concrete", "blue_concrete", "leather_boots", "diamond_boots");

    private MeshTestFixtures() {
    }

    public enum Mode {MESH_ONLY, BOTH, EMPTY, DROP, ANIMATED, INVALID_LATE, INVALID_NORMAL, DEGENERATE_CUT}

    public static void append(Identifier model, ItemStackRenderState.LayerRenderState layer, boolean lastLayer) {
        Mode mode = models.get(model);
        LayerRenderStateAccessor accessor = (LayerRenderStateAccessor) (Object) layer;
        if (mode == null && runtimeRoute && accessor.mismatchedboots$getQuads().stream().anyMatch(quad -> tag(quad) != 0)) {
            mode = Mode.MESH_ONLY;
        }
        if (mode == null) {
            return;
        }
        QuadEmitter emitter = ((FabricLayerRenderState) (Object) layer).emitter();
        for (BakedQuad quad : List.copyOf(accessor.mismatchedboots$getQuads())) {
            if (mode != Mode.EMPTY) {
                emitter.fromBakedQuad(quad).tag(tag(quad));
                if (mode == Mode.ANIMATED) {
                    emitter.animated(true);
                }
                if (mode == Mode.DROP) {
                    for (int index = 0; index < 4; index++) {
                        emitter.pos(index, emitter.x(index) + 2.0F, emitter.y(index), emitter.z(index));
                    }
                }
                emitter.emit();
            }
        }
        if (lastLayer && (mode == Mode.INVALID_LATE || mode == Mode.INVALID_NORMAL)) {
            emitter.pos(0, 0, 0, 0.5F).pos(1, 1, 0, 0.5F)
                   .pos(2, 1, 1, mode == Mode.INVALID_LATE ? 0.6F : 0.5F).pos(3, 0, 1, 0.5F);
            if (mode == Mode.INVALID_NORMAL) {
                // Invalid regardless of where a later layer's transform cuts.
                // Cancellation at an interpolated midpoint has its own test.
                emitter.normal(0, 0, 0, 0);
            }
            emitter.emit();
        }
        if (lastLayer && mode == Mode.DEGENERATE_CUT) {
            float d = 2.0F * QuadClipper.GEOMETRY_EPSILON;
            for (float mirror : new float[]{-1.0F, 1.0F}) {
                emitter.pos(0, 0.5F - d * mirror, 0.5F, 0.5F)
                       .pos(1, 0.5F + (0.5F - d) * mirror, 1, 0.5F)
                       .pos(2, 0.5F + (1.0F - d) * mirror, 0.5F, 0.5F)
                       .pos(3, 0.5F + (0.5F - d) * mirror, 0, 0.5F).emit();
            }
        }
        if (mode != Mode.BOTH && mode != Mode.INVALID_LATE && mode != Mode.INVALID_NORMAL) {
            accessor.mismatchedboots$getQuads().clear();
            layer.setExtents(ItemStackRenderState.LayerRenderState.NO_EXTENTS_SUPPLIER);
        }
    }

    private static int tag(BakedQuad quad) {
        String path = quad.materialInfo().sprite().contents().name().getPath();
        for (int index = 0; index < SPRITES.size(); index++) {
            if (path.contains(SPRITES.get(index))) {
                return 730 + index;
            }
        }
        return 0;
    }

    public static String sprite(int tag) {
        return tag >= 730 && tag < 730 + SPRITES.size() ? SPRITES.get(tag - 730) : "";
    }

    public static void clear() {
        models.clear();
        runtimeRoute = false;
    }
}
