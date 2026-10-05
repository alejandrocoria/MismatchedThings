package games.alejandrocoria.mismatchedboots.client.render.geometry;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class BakedQuadClipper {
    private BakedQuadClipper() {
    }

    public static Result clip(
            BakedQuad input,
            Matrix4fc localTransform,
            QuadClipper.Side side,
            ExtensionMetadata extensionMetadata) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(localTransform, "localTransform");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(extensionMetadata, "extensionMetadata");

        if (extensionMetadata == ExtensionMetadata.UNSUPPORTED) {
            return Result.incompatible(Incompatibility.UNSUPPORTED_EXTENSION_METADATA);
        }
        if (input.direction() == null || input.materialInfo() == null) {
            throw new IllegalArgumentException("Baked quad direction and material info must be present");
        }

        QuadClipper.Vertex[] vertices = new QuadClipper.Vertex[BakedQuad.VERTEX_COUNT];
        for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
            Vector3fc position = input.position(index);
            if (position == null) {
                throw new IllegalArgumentException("Baked quad vertex " + index + " is missing");
            }
            Vector3f clippingPosition = localTransform.transformPosition(new Vector3f(position))
                    .sub(0.5F, 0.5F, 0.5F);
            long packedUv = input.packedUV(index);
            vertices[index] = new QuadClipper.Vertex(
                    point(position),
                    new QuadClipper.Uv(UVPair.unpackU(packedUv), UVPair.unpackV(packedUv)),
                    point(clippingPosition));
        }

        QuadClipper.PolygonResult geometry = QuadClipper.clipPolygon(new QuadClipper.Quad(
                vertices[0], vertices[1], vertices[2], vertices[3]), side);
        return switch (geometry.decision()) {
            case KEEP -> Result.keep(input);
            case DROP -> Result.drop();
            case SPLIT -> Result.split(BakedPolygonTessellator.tessellate(
                    input, Objects.requireNonNull(geometry.polygon())));
            case INCOMPATIBLE -> Result.incompatible(switch (
                    Objects.requireNonNull(geometry.incompatibility())) {
                case NOT_RECTANGLE -> Incompatibility.NOT_RECTANGLE;
                case UNSUPPORTED_TOPOLOGY -> Incompatibility.UNSUPPORTED_TOPOLOGY;
                case DEGENERATE_CUT -> Incompatibility.DEGENERATE_CUT;
            });
        };
    }

    private static QuadClipper.Point point(Vector3fc vector) {
        return new QuadClipper.Point(vector.x(), vector.y(), vector.z());
    }

    public enum ExtensionMetadata {
        NONE,
        UNSUPPORTED
    }

    public enum Incompatibility {
        NOT_RECTANGLE,
        UNSUPPORTED_TOPOLOGY,
        DEGENERATE_CUT,
        UNSUPPORTED_EXTENSION_METADATA
    }

    public record Result(
            QuadClipper.Decision decision,
            List<BakedQuad> quads,
            @Nullable Incompatibility incompatibility) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            quads = List.copyOf(Objects.requireNonNull(quads, "quads"));
            if (decision == QuadClipper.Decision.KEEP
                    && (quads.size() != 1 || incompatibility != null)) {
                throw new IllegalArgumentException("KEEP requires exactly one baked quad");
            }
            if (decision == QuadClipper.Decision.SPLIT
                    && (quads.isEmpty() || quads.size() > 2 || incompatibility != null)) {
                throw new IllegalArgumentException("SPLIT requires one or two baked quads");
            }
            if (decision == QuadClipper.Decision.INCOMPATIBLE
                    && (!quads.isEmpty() || incompatibility == null)) {
                throw new IllegalArgumentException("INCOMPATIBLE requires only a reason");
            }
            if (decision == QuadClipper.Decision.DROP
                    && (!quads.isEmpty() || incompatibility != null)) {
                throw new IllegalArgumentException("DROP cannot carry baked quads or a reason");
            }
        }

        static Result keep(BakedQuad quad) {
            return new Result(QuadClipper.Decision.KEEP, List.of(quad), null);
        }

        static Result drop() {
            return new Result(QuadClipper.Decision.DROP, List.of(), null);
        }

        static Result split(List<BakedQuad> quads) {
            return new Result(QuadClipper.Decision.SPLIT, quads, null);
        }

        static Result incompatible(Incompatibility incompatibility) {
            return new Result(QuadClipper.Decision.INCOMPATIBLE, List.of(), incompatibility);
        }
    }
}
