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
        validatePolygon(polygon);

        List<BakedQuad> result = new ArrayList<>(2);
        switch (polygon.vertices().size()) {
            case 3 -> result.add(rebuild(source,
                    polygon.vertex(0), polygon.vertex(1), polygon.vertex(2), polygon.vertex(2)));
            case 4 -> result.add(rebuild(source,
                    polygon.vertex(0), polygon.vertex(1), polygon.vertex(2), polygon.vertex(3)));
            case 5 -> {
                result.add(rebuild(source,
                        polygon.vertex(0), polygon.vertex(1), polygon.vertex(2), polygon.vertex(3)));
                result.add(rebuild(source,
                        polygon.vertex(0), polygon.vertex(3), polygon.vertex(4), polygon.vertex(4)));
            }
            default -> throw new IllegalStateException("Unexpected clipped polygon size");
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

    private static void validatePolygon(QuadClipper.Polygon polygon) {
        float epsilonSquared = QuadClipper.GEOMETRY_EPSILON * QuadClipper.GEOMETRY_EPSILON;
        for (int index = 0; index < polygon.vertices().size(); index++) {
            QuadClipper.Vertex vertex = polygon.vertex(index);
            if (!vertex.position().isFinite()
                    || !vertex.clippingPosition().isFinite()
                    || !vertex.uv().isFinite()) {
                throw new IllegalArgumentException("Polygon vertex " + index + " contains a non-finite value");
            }
            for (int other = index + 1; other < polygon.vertices().size(); other++) {
                if (vertex.position().distanceSquared(polygon.vertex(other).position()) <= epsilonSquared) {
                    throw new IllegalArgumentException("Polygon contains duplicate rendered positions");
                }
            }
        }

        QuadClipper.Point origin = polygon.vertex(0).position();
        QuadClipper.Point normal = null;
        for (int index = 1; index < polygon.vertices().size() - 1; index++) {
            QuadClipper.Point candidate = polygon.vertex(index).position().subtract(origin)
                    .cross(polygon.vertex(index + 1).position().subtract(origin));
            if (candidate.lengthSquared() > epsilonSquared) {
                normal = candidate;
                break;
            }
        }
        if (normal == null) {
            throw new IllegalArgumentException("Polygon has no renderable area");
        }

        float normalLength = normal.length();
        for (QuadClipper.Vertex vertex : polygon.vertices()) {
            float distance = Math.abs(vertex.position().subtract(origin).dot(normal)) / normalLength;
            if (distance > QuadClipper.GEOMETRY_EPSILON) {
                throw new IllegalArgumentException("Polygon is not planar");
            }
        }

        for (int index = 0; index < polygon.vertices().size(); index++) {
            QuadClipper.Point firstEdge = polygon.vertex((index + 1) % polygon.vertices().size())
                    .position().subtract(polygon.vertex(index).position());
            QuadClipper.Point secondEdge = polygon.vertex((index + 2) % polygon.vertices().size())
                    .position().subtract(polygon.vertex((index + 1) % polygon.vertices().size()).position());
            float firstLength = firstEdge.length();
            float secondLength = secondEdge.length();
            if (firstLength <= QuadClipper.GEOMETRY_EPSILON
                    || secondLength <= QuadClipper.GEOMETRY_EPSILON
                    || firstEdge.cross(secondEdge).dot(normal)
                    <= QuadClipper.GEOMETRY_EPSILON * firstLength * secondLength * normalLength) {
                throw new IllegalArgumentException("Polygon must be strictly convex with consistent winding");
            }
        }
    }

    private static long packedUv(QuadClipper.Vertex vertex) {
        return UVPair.pack(vertex.uv().u(), vertex.uv().v());
    }

    private static Vector3f vector(QuadClipper.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }
}
