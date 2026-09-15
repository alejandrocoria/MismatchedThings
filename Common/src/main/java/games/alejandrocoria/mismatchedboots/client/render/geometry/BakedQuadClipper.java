package games.alejandrocoria.mismatchedboots.client.render.geometry;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

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

        QuadClipper.Result geometry = QuadClipper.clip(new QuadClipper.Quad(
                vertices[0], vertices[1], vertices[2], vertices[3]), side);
        return switch (geometry.decision()) {
            case KEEP -> Result.keep(input);
            case DROP -> Result.drop();
            case SPLIT -> Result.split(rebuild(input, Objects.requireNonNull(geometry.quad())));
            case INCOMPATIBLE -> Result.incompatible(switch (
                    Objects.requireNonNull(geometry.incompatibility())) {
                case NOT_RECTANGLE -> Incompatibility.NOT_RECTANGLE;
                case UNSUPPORTED_TOPOLOGY -> Incompatibility.UNSUPPORTED_TOPOLOGY;
            });
        };
    }

    private static BakedQuad rebuild(BakedQuad source, QuadClipper.Quad geometry) {
        QuadClipper.Vertex vertex0 = geometry.vertex(0);
        QuadClipper.Vertex vertex1 = geometry.vertex(1);
        QuadClipper.Vertex vertex2 = geometry.vertex(2);
        QuadClipper.Vertex vertex3 = geometry.vertex(3);
        return new BakedQuad(
                vector(vertex0.position()),
                vector(vertex1.position()),
                vector(vertex2.position()),
                vector(vertex3.position()),
                packedUv(vertex0),
                packedUv(vertex1),
                packedUv(vertex2),
                packedUv(vertex3),
                source.direction(),
                source.materialInfo());
    }

    private static long packedUv(QuadClipper.Vertex vertex) {
        return UVPair.pack(vertex.uv().u(), vertex.uv().v());
    }

    private static QuadClipper.Point point(Vector3fc vector) {
        return new QuadClipper.Point(vector.x(), vector.y(), vector.z());
    }

    private static Vector3f vector(QuadClipper.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }

    public enum ExtensionMetadata {
        NONE,
        UNSUPPORTED
    }

    public enum Incompatibility {
        NOT_RECTANGLE,
        UNSUPPORTED_TOPOLOGY,
        UNSUPPORTED_EXTENSION_METADATA
    }

    public record Result(
            QuadClipper.Decision decision,
            @Nullable BakedQuad quad,
            @Nullable Incompatibility incompatibility) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            if ((decision == QuadClipper.Decision.KEEP || decision == QuadClipper.Decision.SPLIT)
                    && (quad == null || incompatibility != null)) {
                throw new IllegalArgumentException(decision + " requires only a baked quad");
            }
            if (decision == QuadClipper.Decision.INCOMPATIBLE
                    && (quad != null || incompatibility == null)) {
                throw new IllegalArgumentException("INCOMPATIBLE requires only a reason");
            }
            if (decision == QuadClipper.Decision.DROP
                    && (quad != null || incompatibility != null)) {
                throw new IllegalArgumentException("DROP cannot carry a baked quad or reason");
            }
        }

        static Result keep(BakedQuad quad) {
            return new Result(QuadClipper.Decision.KEEP, quad, null);
        }

        static Result drop() {
            return new Result(QuadClipper.Decision.DROP, null, null);
        }

        static Result split(BakedQuad quad) {
            return new Result(QuadClipper.Decision.SPLIT, quad, null);
        }

        static Result incompatible(Incompatibility incompatibility) {
            return new Result(QuadClipper.Decision.INCOMPATIBLE, null, incompatibility);
        }
    }
}
