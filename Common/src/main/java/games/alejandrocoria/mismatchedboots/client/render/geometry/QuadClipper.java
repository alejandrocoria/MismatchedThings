package games.alejandrocoria.mismatchedboots.client.render.geometry;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QuadClipper {
    public static final float GEOMETRY_EPSILON = 0.000001F;

    private QuadClipper() {
    }

    public static Result clip(Quad input, Side side) {
        PolygonResult polygonResult = clipPolygon(input, side);
        return switch (polygonResult.decision()) {
            case KEEP -> Result.keep();
            case DROP -> Result.drop();
            case INCOMPATIBLE -> Result.incompatible(
                    Objects.requireNonNull(polygonResult.incompatibility()));
            case SPLIT -> {
                Polygon polygon = Objects.requireNonNull(polygonResult.polygon());
                if (polygon.vertices().size() != Quad.VERTEX_COUNT) {
                    yield Result.incompatible(Incompatibility.UNSUPPORTED_TOPOLOGY);
                }
                yield Result.split(new Quad(polygon.vertices()));
            }
        };
    }

    public static PolygonResult clipPolygon(Quad input, Side side) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(side, "side");
        validateFinite(input);

        boolean hasLeft = false;
        boolean hasRight = false;
        boolean hasCenter = false;
        for (Vertex vertex : input.vertices()) {
            float x = vertex.clippingPosition().x();
            hasLeft |= x < -GEOMETRY_EPSILON;
            hasRight |= x > GEOMETRY_EPSILON;
            hasCenter |= Math.abs(x) <= GEOMETRY_EPSILON;
        }

        if (!hasLeft && !hasRight) {
            return side == Side.RIGHT ? PolygonResult.keep() : PolygonResult.drop();
        }
        if (!hasRight) {
            return side == Side.LEFT ? PolygonResult.keep() : PolygonResult.drop();
        }
        if (!hasLeft) {
            return side == Side.RIGHT ? PolygonResult.keep() : PolygonResult.drop();
        }

        if (!isRectangle(input)) {
            return PolygonResult.incompatible(Incompatibility.NOT_RECTANGLE);
        }
        if (hasCenter) {
            return PolygonResult.incompatible(Incompatibility.UNSUPPORTED_TOPOLOGY);
        }

        List<Integer> crossingEdges = crossingEdges(input);
        if (crossingEdges.size() != 2) {
            return PolygonResult.incompatible(Incompatibility.UNSUPPORTED_TOPOLOGY);
        }

        Polygon clipped = clipRectangle(input, side);
        validateOutput(input, clipped, side);
        return PolygonResult.split(clipped);
    }

    private static List<Integer> crossingEdges(Quad quad) {
        List<Integer> crossingEdges = new ArrayList<>(2);
        for (int index = 0; index < Quad.VERTEX_COUNT; index++) {
            float currentX = quad.vertex(index).clippingPosition().x();
            float nextX = quad.vertex((index + 1) % Quad.VERTEX_COUNT).clippingPosition().x();
            if ((currentX < -GEOMETRY_EPSILON && nextX > GEOMETRY_EPSILON)
                    || (currentX > GEOMETRY_EPSILON && nextX < -GEOMETRY_EPSILON)) {
                crossingEdges.add(index);
            }
        }
        return crossingEdges;
    }

    private static Polygon clipRectangle(Quad input, Side side) {
        List<Vertex> output = new ArrayList<>(Quad.VERTEX_COUNT + 1);
        for (int index = 0; index < Quad.VERTEX_COUNT; index++) {
            Vertex current = input.vertex(index);
            Vertex next = input.vertex((index + 1) % Quad.VERTEX_COUNT);
            boolean currentInside = side.contains(current.clippingPosition().x());
            boolean nextInside = side.contains(next.clippingPosition().x());
            if (currentInside) {
                output.add(current);
            }
            if (currentInside != nextInside) {
                output.add(intersection(current, next));
            }
        }
        return new Polygon(output);
    }

    private static Vertex intersection(Vertex first, Vertex second) {
        float firstX = first.clippingPosition().x();
        float secondX = second.clippingPosition().x();
        float factor = firstX / (firstX - secondX);
        if (!Float.isFinite(factor) || factor <= 0.0F || factor >= 1.0F) {
            throw new IllegalStateException("A supported crossing produced an invalid intersection factor: "
                    + factor);
        }

        Point clipping = first.clippingPosition().lerp(second.clippingPosition(), factor);
        return new Vertex(
                first.position().lerp(second.position(), factor),
                first.uv().lerp(second.uv(), factor),
                new Point(0.0F, clipping.y(), clipping.z()));
    }

    private static boolean isRectangle(Quad quad) {
        for (int first = 0; first < Quad.VERTEX_COUNT; first++) {
            for (int second = first + 1; second < Quad.VERTEX_COUNT; second++) {
                if (quad.vertex(first).clippingPosition()
                        .distanceSquared(quad.vertex(second).clippingPosition())
                        <= GEOMETRY_EPSILON * GEOMETRY_EPSILON) {
                    return false;
                }
            }
        }

        Point edge0 = edge(quad, 0);
        Point edge1 = edge(quad, 1);
        Point edge2 = edge(quad, 2);
        Point edge3 = edge(quad, 3);
        float length0 = edge0.length();
        float length1 = edge1.length();
        float length2 = edge2.length();
        float length3 = edge3.length();
        if (minimum(length0, length1, length2, length3) <= GEOMETRY_EPSILON) {
            return false;
        }

        float parallel02 = edge0.cross(edge2).length() / (length0 * length2);
        float parallel13 = edge1.cross(edge3).length() / (length1 * length3);
        float orthogonal = Math.abs(edge0.dot(edge1)) / (length0 * length1);
        float lengthDifference02 = Math.abs(length0 - length2) / Math.max(length0, length2);
        float lengthDifference13 = Math.abs(length1 - length3) / Math.max(length1, length3);
        Point normal = edge0.cross(edge1);
        float normalLength = normal.length();
        if (normalLength <= GEOMETRY_EPSILON) {
            return false;
        }
        float planarity = Math.abs(quad.vertex(3).clippingPosition()
                .subtract(quad.vertex(0).clippingPosition()).dot(normal)) / normalLength;
        if (maximum(parallel02, parallel13, orthogonal,
                lengthDifference02, lengthDifference13, planarity) > GEOMETRY_EPSILON) {
            return false;
        }

        for (int index = 0; index < Quad.VERTEX_COUNT; index++) {
            Point cornerNormal = edge(quad, index).cross(edge(quad, (index + 1) % Quad.VERTEX_COUNT));
            if (cornerNormal.dot(normal) <= GEOMETRY_EPSILON * normalLength) {
                return false;
            }
        }
        return true;
    }

    private static Point edge(Quad quad, int index) {
        return quad.vertex((index + 1) % Quad.VERTEX_COUNT).clippingPosition()
                .subtract(quad.vertex(index).clippingPosition());
    }

    private static void validateFinite(Quad quad) {
        for (int index = 0; index < Quad.VERTEX_COUNT; index++) {
            Vertex vertex = quad.vertex(index);
            if (!vertex.position().isFinite()
                    || !vertex.clippingPosition().isFinite()
                    || !vertex.uv().isFinite()) {
                throw new IllegalArgumentException("Quad vertex " + index + " contains a non-finite value");
            }
        }
    }

    private static void validateOutput(Quad input, Polygon output, Side side) {
        validateFinite(output);
        Point inputNormal = edge(input, 0).cross(edge(input, 1));
        Point outputNormal = polygonNormal(output);
        if (outputNormal.lengthSquared() <= GEOMETRY_EPSILON * GEOMETRY_EPSILON
                || outputNormal.dot(inputNormal) <= 0.0F) {
            throw new IllegalStateException("A supported cut produced invalid area or winding");
        }
        for (int first = 0; first < output.vertices().size(); first++) {
            for (int second = first + 1; second < output.vertices().size(); second++) {
                if (output.vertex(first).clippingPosition()
                        .distanceSquared(output.vertex(second).clippingPosition())
                        <= GEOMETRY_EPSILON * GEOMETRY_EPSILON) {
                    throw new IllegalStateException("A supported cut produced duplicate vertices");
                }
            }
        }
        for (Vertex vertex : output.vertices()) {
            if (Math.abs(vertex.clippingPosition().x()) > GEOMETRY_EPSILON
                    && !input.vertices().contains(vertex)) {
                throw new IllegalStateException("A generated intersection does not lie on the clipping plane");
            }
            if (!side.containsWithBoundary(vertex.clippingPosition().x())) {
                throw new IllegalStateException("A supported cut retained a vertex owned by the other side");
            }
        }
    }

    private static Point polygonNormal(Polygon polygon) {
        Point origin = polygon.vertex(0).clippingPosition();
        for (int index = 1; index < polygon.vertices().size() - 1; index++) {
            Point normal = polygon.vertex(index).clippingPosition().subtract(origin)
                    .cross(polygon.vertex(index + 1).clippingPosition().subtract(origin));
            if (normal.lengthSquared() > GEOMETRY_EPSILON * GEOMETRY_EPSILON) {
                return normal;
            }
        }
        return new Point(0.0F, 0.0F, 0.0F);
    }

    private static void validateFinite(Polygon polygon) {
        for (int index = 0; index < polygon.vertices().size(); index++) {
            Vertex vertex = polygon.vertex(index);
            if (!vertex.position().isFinite()
                    || !vertex.clippingPosition().isFinite()
                    || !vertex.uv().isFinite()) {
                throw new IllegalArgumentException("Polygon vertex " + index + " contains a non-finite value");
            }
        }
    }

    private static float minimum(float... values) {
        float result = Float.POSITIVE_INFINITY;
        for (float value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static float maximum(float... values) {
        float result = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    public enum Side {
        LEFT {
            @Override
            boolean contains(float x) {
                return x < 0.0F;
            }
        },
        RIGHT {
            @Override
            boolean contains(float x) {
                return x >= 0.0F;
            }
        };

        abstract boolean contains(float x);

        boolean containsWithBoundary(float x) {
            return this == LEFT ? x <= GEOMETRY_EPSILON : x >= -GEOMETRY_EPSILON;
        }
    }

    public enum Decision {
        KEEP,
        DROP,
        SPLIT,
        INCOMPATIBLE
    }

    public enum Incompatibility {
        NOT_RECTANGLE,
        UNSUPPORTED_TOPOLOGY
    }

    public record Point(float x, float y, float z) {
        Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        float dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

        float length() {
            return (float) Math.sqrt(lengthSquared());
        }

        float lengthSquared() {
            return dot(this);
        }

        float distanceSquared(Point other) {
            return subtract(other).lengthSquared();
        }

        Point lerp(Point other, float factor) {
            return new Point(
                    x + (other.x - x) * factor,
                    y + (other.y - y) * factor,
                    z + (other.z - z) * factor);
        }

        boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
        }
    }

    public record Uv(float u, float v) {
        Uv lerp(Uv other, float factor) {
            return new Uv(
                    u + (other.u - u) * factor,
                    v + (other.v - v) * factor);
        }

        boolean isFinite() {
            return Float.isFinite(u) && Float.isFinite(v);
        }
    }

    public record Vertex(Point position, Uv uv, Point clippingPosition) {
        public Vertex {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(uv, "uv");
            Objects.requireNonNull(clippingPosition, "clippingPosition");
        }
    }

    public record Quad(List<Vertex> vertices) {
        static final int VERTEX_COUNT = 4;

        public Quad {
            vertices = List.copyOf(vertices);
            if (vertices.size() != VERTEX_COUNT) {
                throw new IllegalArgumentException("A quad must contain exactly four vertices");
            }
        }

        public Quad(Vertex vertex0, Vertex vertex1, Vertex vertex2, Vertex vertex3) {
            this(List.of(vertex0, vertex1, vertex2, vertex3));
        }

        public Vertex vertex(int index) {
            return vertices.get(index);
        }
    }

    public record Polygon(List<Vertex> vertices) {
        private static final int MINIMUM_VERTICES = 3;
        private static final int MAXIMUM_VERTICES = 5;

        public Polygon {
            vertices = List.copyOf(vertices);
            if (vertices.size() < MINIMUM_VERTICES || vertices.size() > MAXIMUM_VERTICES) {
                throw new IllegalArgumentException("A clipped polygon must contain three to five vertices");
            }
        }

        public Vertex vertex(int index) {
            return vertices.get(index);
        }
    }

    public record PolygonResult(
            Decision decision,
            @Nullable Polygon polygon,
            @Nullable Incompatibility incompatibility) {
        public PolygonResult {
            Objects.requireNonNull(decision, "decision");
            if (decision == Decision.SPLIT && (polygon == null || incompatibility != null)) {
                throw new IllegalArgumentException("SPLIT requires only a clipped polygon");
            }
            if (decision == Decision.INCOMPATIBLE && (polygon != null || incompatibility == null)) {
                throw new IllegalArgumentException("INCOMPATIBLE requires only a reason");
            }
            if ((decision == Decision.KEEP || decision == Decision.DROP)
                    && (polygon != null || incompatibility != null)) {
                throw new IllegalArgumentException(decision + " cannot carry a polygon or reason");
            }
        }

        static PolygonResult keep() {
            return new PolygonResult(Decision.KEEP, null, null);
        }

        static PolygonResult drop() {
            return new PolygonResult(Decision.DROP, null, null);
        }

        static PolygonResult split(Polygon polygon) {
            return new PolygonResult(Decision.SPLIT, polygon, null);
        }

        static PolygonResult incompatible(Incompatibility incompatibility) {
            return new PolygonResult(Decision.INCOMPATIBLE, null, incompatibility);
        }
    }

    public record Result(
            Decision decision,
            @Nullable Quad quad,
            @Nullable Incompatibility incompatibility) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            if (decision == Decision.SPLIT && (quad == null || incompatibility != null)) {
                throw new IllegalArgumentException("SPLIT requires only a reconstructed quad");
            }
            if (decision == Decision.INCOMPATIBLE && (quad != null || incompatibility == null)) {
                throw new IllegalArgumentException("INCOMPATIBLE requires only a reason");
            }
            if ((decision == Decision.KEEP || decision == Decision.DROP)
                    && (quad != null || incompatibility != null)) {
                throw new IllegalArgumentException(decision + " cannot carry a quad or reason");
            }
        }

        static Result keep() {
            return new Result(Decision.KEEP, null, null);
        }

        static Result drop() {
            return new Result(Decision.DROP, null, null);
        }

        static Result split(Quad quad) {
            return new Result(Decision.SPLIT, quad, null);
        }

        static Result incompatible(Incompatibility incompatibility) {
            return new Result(Decision.INCOMPATIBLE, null, incompatibility);
        }
    }
}
