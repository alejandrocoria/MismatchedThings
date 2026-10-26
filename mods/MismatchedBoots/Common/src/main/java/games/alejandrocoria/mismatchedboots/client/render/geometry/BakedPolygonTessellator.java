package games.alejandrocoria.mismatchedboots.client.render.geometry;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BakedPolygonTessellator {
    private BakedPolygonTessellator() {
    }

    public static List<BakedQuad> tessellate(BakedQuad source, QuadClipper.Polygon polygon) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(polygon, "polygon");
        if (source.direction() == null || source.materialInfo() == null) {
            throw new IllegalArgumentException("Baked quad direction and material info must be present");
        }
        List<BakedQuad> result = new ArrayList<>(2);
        for (PolygonTessellator.Primitive primitive : PolygonTessellator.tessellate(polygon)) {
            result.add(rebuild(source,
                               polygon.vertex(primitive.first()), polygon.vertex(primitive.second()),
                               polygon.vertex(primitive.third()), polygon.vertex(primitive.fourth())));
        }
        return List.copyOf(result);
    }

    private static BakedQuad rebuild(
            BakedQuad source,
            QuadClipper.Vertex vertex0,
            QuadClipper.Vertex vertex1,
            QuadClipper.Vertex vertex2,
            QuadClipper.Vertex vertex3) {
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

    private static Vector3f vector(QuadClipper.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }
}
