package games.alejandrocoria.mismatchedboots.client.render.geometry;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Plans into a detached renderer-owned mesh; source layers are not touched until commit. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricMeshClipper {
    private FabricMeshClipper() {
    }

    public static LayerGeometryExtension.Plan plan(
            @Nullable MutableMesh source, Matrix4fc localTransform, QuadClipper.Side side) {
        Objects.requireNonNull(localTransform, "localTransform");
        Objects.requireNonNull(side, "side");
        if (source == null || source.size() == 0) {
            return LayerGeometryExtension.Plan.NONE;
        }
        MutableMesh builder = Renderer.get().mutableMesh();
        QuadEmitter emitter = builder.emitter();
        boolean[] compatible = {true};
        boolean[] animated = {false};
        source.forEach(quad -> {
            if (!compatible[0]) {
                return;
            }
            try {
                QuadClipper.TracedPolygonResult result = clip(quad, localTransform, side);
                switch (result.geometry().decision()) {
                    case KEEP -> emitter.copyFrom(quad).emit();
                    case DROP -> {
                        return;
                    }
                    case INCOMPATIBLE -> {
                        compatible[0] = false;
                        return;
                    }
                    case SPLIT -> emitSplit(quad, result, emitter);
                }
                animated[0] |= quad.animated() || (quad.foilType() != null
                        && quad.foilType() != ItemStackRenderState.FoilType.NONE);
            } catch (InvalidMeshException exception) {
                compatible[0] = false;
            }
        });
        if (!compatible[0]) {
            return LayerGeometryExtension.Plan.INCOMPATIBLE;
        }
        Mesh replacement = builder.immutableCopy();
        return new LayerGeometryExtension.Plan(true, () -> {
            source.clear();
            if (replacement.size() != 0) {
                replacement.outputTo(source.emitter());
            }
        }, animated[0]);
    }

    private static QuadClipper.TracedPolygonResult clip(
            QuadView source, Matrix4fc localTransform, QuadClipper.Side side) {
        List<QuadClipper.Vertex> vertices = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            Vector3f raw = source.copyPos(index, null);
            Vector3f canonical = localTransform.transformPosition(new Vector3f(raw)).sub(0.5F, 0.5F, 0.5F);
            if (!raw.isFinite() || !canonical.isFinite()
                    || !Float.isFinite(source.u(index)) || !Float.isFinite(source.v(index))) {
                throw new InvalidMeshException();
            }
            if (source.hasNormal(index)) {
                validateNormal(source.copyNormal(index, null));
            }
            vertices.add(new QuadClipper.Vertex(point(raw),
                                                new QuadClipper.Uv(source.u(index), source.v(index)), point(canonical)));
        }
        return QuadClipper.clipPolygonWithOrigins(new QuadClipper.Quad(vertices), side);
    }

    private static void emitSplit(
            QuadView source, QuadClipper.TracedPolygonResult result, QuadEmitter emitter) {
        QuadClipper.Polygon polygon = Objects.requireNonNull(result.geometry().polygon());
        // Validate and prepare every attribute before emitting any candidate primitive.
        List<VertexData> vertices = new ArrayList<>(polygon.vertices().size());
        for (int index = 0; index < polygon.vertices().size(); index++) {
            QuadClipper.Vertex vertex = polygon.vertex(index);
            QuadClipper.VertexOrigin origin = result.origins().get(index);
            int first = origin.first();
            int second = origin.second();
            float t = origin.factor();
            Vector3f normal = null;
            if (origin.retained()) {
                normal = source.copyNormal(first, null);
            } else if (source.hasNormal(first) || source.hasNormal(second)) {
                Vector3f n0 = effectiveNormal(source, first);
                Vector3f n1 = effectiveNormal(source, second);
                normal = n0.lerp(n1, t);
                validateNormal(normal);
                normal.normalize();
            }
            vertices.add(new VertexData(vertex,
                                        origin.retained() ? source.color(first) : interpolateColor(source.color(first), source.color(second), t),
                                        origin.retained()
                                        ? source.lightmap(first)
                                        : interpolateLight(source.lightmap(first), source.lightmap(second), t),
                                        normal));
        }
        for (PolygonTessellator.Primitive primitive : PolygonTessellator.tessellate(polygon)) {
            emitter.clear();
            copyProperties(source, emitter);
            for (int index = 0; index < 4; index++) {
                VertexData data = vertices.get(primitive.vertex(index));
                QuadClipper.Point raw = data.geometry().position();
                emitter.pos(index, raw.x(), raw.y(), raw.z())
                       .uv(index, data.geometry().uv().u(), data.geometry().uv().v())
                       .color(index, data.color()).lightmap(index, data.lightmap());
                if (data.normal() != null) {
                    emitter.normal(index, data.normal());
                }
            }
            emitter.emit();
        }
    }

    private static void copyProperties(QuadView source, QuadEmitter target) {
        target.cullFace(source.cullFace()).nominalFace(source.nominalFace())
              .atlas(source.atlas()).itemRenderType(source.itemRenderType()).chunkLayer(source.chunkLayer())
              .emissive(source.emissive()).diffuseShade(source.diffuseShade())
              .ambientOcclusion(source.ambientOcclusion()).shadeMode(source.shadeMode())
              .foilType(source.foilType()).animated(source.animated()).tintIndex(source.tintIndex()).tag(source.tag());
    }

    private static Vector3f effectiveNormal(QuadView source, int index) {
        Vector3f result = source.hasNormal(index) ? source.copyNormal(index, null) : new Vector3f(source.faceNormal());
        validateNormal(result);
        return result;
    }

    private static void validateNormal(@Nullable Vector3f normal) {
        if (normal == null || !normal.isFinite()
                || normal.lengthSquared() <= QuadClipper.GEOMETRY_EPSILON * QuadClipper.GEOMETRY_EPSILON) {
            throw new InvalidMeshException();
        }
    }

    private static int interpolateColor(int first, int second, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (first >>> shift) & 255;
            int b = (second >>> shift) & 255;
            result |= interpolate(a, b, t) << shift;
        }
        return result;
    }

    private static int interpolateLight(int first, int second, float t) {
        return LightCoordsUtil.smoothPack(
                interpolate(LightCoordsUtil.smoothBlock(first), LightCoordsUtil.smoothBlock(second), t),
                interpolate(LightCoordsUtil.smoothSky(first), LightCoordsUtil.smoothSky(second), t));
    }

    private static int interpolate(int first, int second, float t) {
        return Math.clamp(Math.round(first + (second - first) * t), Math.min(first, second), Math.max(first, second));
    }

    private static QuadClipper.Point point(Vector3f point) {
        return new QuadClipper.Point(point.x(), point.y(), point.z());
    }

    private record VertexData(QuadClipper.Vertex geometry, int color, int lightmap, @Nullable Vector3f normal) {
    }

    private static final class InvalidMeshException extends RuntimeException {
    }
}
