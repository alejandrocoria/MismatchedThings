package games.alejandrocoria.mismatchedboots.client.render.geometry;

import java.util.List;
import java.util.Objects;

public final class PolygonTessellator {
    private PolygonTessellator() {
    }

    public static List<Primitive> tessellate(QuadClipper.Polygon polygon) {
        Objects.requireNonNull(polygon, "polygon");
        validatePolygon(polygon);
        return switch (polygon.vertices().size()) {
            case 3 -> List.of(new Primitive(0, 1, 2, 2));
            case 4 -> List.of(new Primitive(0, 1, 2, 3));
            case 5 -> List.of(new Primitive(0, 1, 2, 3), new Primitive(0, 3, 4, 4));
            default -> throw new IllegalStateException("Unexpected clipped polygon size");
        };
    }

    public record Primitive(int first, int second, int third, int fourth) {
        public int vertex(int index) {
            return switch (index) {
                case 0 -> first;
                case 1 -> second;
                case 2 -> third;
                case 3 -> fourth;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
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

}
