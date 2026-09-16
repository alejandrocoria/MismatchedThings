package games.alejandrocoria.mismatchedboots.client.render.geometry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadClipperTest {
    private static final float EPSILON = QuadClipper.GEOMETRY_EPSILON;

    @Test
    void keepsAndDropsWhollyOwnedQuadsSymmetrically() {
        QuadClipper.Quad left = rectangle(-2.0F, -1.0F, -1.0F, 1.0F);
        QuadClipper.Quad right = rectangle(1.0F, 2.0F, -1.0F, 1.0F);

        assertDecision(left, QuadClipper.Side.LEFT, QuadClipper.Decision.KEEP);
        assertDecision(left, QuadClipper.Side.RIGHT, QuadClipper.Decision.DROP);
        assertDecision(right, QuadClipper.Side.LEFT, QuadClipper.Decision.DROP);
        assertDecision(right, QuadClipper.Side.RIGHT, QuadClipper.Decision.KEEP);
    }

    @Test
    void givesTheExactCenterAndCenterTouchingEdgeToOnlyOneSide() {
        QuadClipper.Quad onCenter = new QuadClipper.Quad(
                vertex(0.0F, -1.0F, -1.0F),
                vertex(0.0F, -1.0F, 1.0F),
                vertex(0.0F, 1.0F, 1.0F),
                vertex(0.0F, 1.0F, -1.0F));
        QuadClipper.Quad touchesFromLeft = rectangle(-1.0F, 0.0F, -1.0F, 1.0F);
        QuadClipper.Quad touchesFromRight = rectangle(0.0F, 1.0F, -1.0F, 1.0F);

        assertDecision(onCenter, QuadClipper.Side.LEFT, QuadClipper.Decision.DROP);
        assertDecision(onCenter, QuadClipper.Side.RIGHT, QuadClipper.Decision.KEEP);
        assertDecision(touchesFromLeft, QuadClipper.Side.LEFT, QuadClipper.Decision.KEEP);
        assertDecision(touchesFromLeft, QuadClipper.Side.RIGHT, QuadClipper.Decision.DROP);
        assertDecision(touchesFromRight, QuadClipper.Side.LEFT, QuadClipper.Decision.DROP);
        assertDecision(touchesFromRight, QuadClipper.Side.RIGHT, QuadClipper.Decision.KEEP);
    }

    @Test
    void splitsBothSidesUsingHandAuthoredPositionAndUvOracles() {
        QuadClipper.Vertex vertex0 = vertex(-2.0F, -1.0F, 0.0F, 10.0F, 20.0F);
        QuadClipper.Vertex vertex1 = vertex(2.0F, -1.0F, 0.0F, 30.0F, 24.0F);
        QuadClipper.Vertex vertex2 = vertex(2.0F, 1.0F, 0.0F, 34.0F, 44.0F);
        QuadClipper.Vertex vertex3 = vertex(-2.0F, 1.0F, 0.0F, 14.0F, 40.0F);
        QuadClipper.Quad input = new QuadClipper.Quad(vertex0, vertex1, vertex2, vertex3);

        QuadClipper.Quad left = split(input, QuadClipper.Side.LEFT);
        QuadClipper.Quad right = split(input, QuadClipper.Side.RIGHT);

        assertSame(vertex0, left.vertex(0));
        assertVertex(left.vertex(1), 0.0F, -1.0F, 0.0F, 20.0F, 22.0F);
        assertVertex(left.vertex(2), 0.0F, 1.0F, 0.0F, 24.0F, 42.0F);
        assertSame(vertex3, left.vertex(3));

        assertVertex(right.vertex(0), 0.0F, -1.0F, 0.0F, 20.0F, 22.0F);
        assertSame(vertex1, right.vertex(1));
        assertSame(vertex2, right.vertex(2));
        assertVertex(right.vertex(3), 0.0F, 1.0F, 0.0F, 24.0F, 42.0F);
    }

    @Test
    void clipsAdjacentEdgesIntoHandAuthoredTriangleAndPentagon() {
        QuadClipper.Vertex vertex0 = vertex(0.5F, -1.0F, 0.0F, 10.0F, 20.0F);
        QuadClipper.Vertex vertex1 = vertex(1.5F, 0.0F, 0.0F, 30.0F, 24.0F);
        QuadClipper.Vertex vertex2 = vertex(0.5F, 1.0F, 0.0F, 34.0F, 44.0F);
        QuadClipper.Vertex vertex3 = vertex(-0.5F, 0.0F, 0.0F, 14.0F, 40.0F);
        QuadClipper.Quad input = new QuadClipper.Quad(vertex0, vertex1, vertex2, vertex3);

        QuadClipper.Polygon left = splitPolygon(input, QuadClipper.Side.LEFT);
        QuadClipper.Polygon right = splitPolygon(input, QuadClipper.Side.RIGHT);

        assertEquals(3, left.vertices().size());
        assertVertex(left.vertex(0), 0.0F, 0.5F, 0.0F, 24.0F, 42.0F);
        assertSame(vertex3, left.vertex(1));
        assertVertex(left.vertex(2), 0.0F, -0.5F, 0.0F, 12.0F, 30.0F);

        assertEquals(5, right.vertices().size());
        assertSame(vertex0, right.vertex(0));
        assertSame(vertex1, right.vertex(1));
        assertSame(vertex2, right.vertex(2));
        assertVertex(right.vertex(3), 0.0F, 0.5F, 0.0F, 24.0F, 42.0F);
        assertVertex(right.vertex(4), 0.0F, -0.5F, 0.0F, 12.0F, 30.0F);

        assertTrue(normal(left).dot(normal(input)) > 0.0F);
        assertTrue(normal(right).dot(normal(input)) > 0.0F);
        assertIncompatible(input, QuadClipper.Incompatibility.UNSUPPORTED_TOPOLOGY);
    }

    @Test
    void clipsAdjacentEdgesForEveryCyclicStartAndBothValidWindings() {
        List<QuadClipper.Vertex> forward = adjacentEdgeRectangle().vertices();
        List<QuadClipper.Vertex> reverse = List.of(
                forward.get(0), forward.get(3), forward.get(2), forward.get(1));

        for (List<QuadClipper.Vertex> winding : List.of(forward, reverse)) {
            for (int rotation = 0; rotation < 4; rotation++) {
                QuadClipper.Quad input = new QuadClipper.Quad(rotate(winding, rotation));
                QuadClipper.Point inputNormal = normal(input);
                QuadClipper.Polygon left = splitPolygon(input, QuadClipper.Side.LEFT);
                QuadClipper.Polygon right = splitPolygon(input, QuadClipper.Side.RIGHT);

                assertEquals(3, left.vertices().size());
                assertEquals(5, right.vertices().size());
                assertTrue(normal(left).dot(inputNormal) > 0.0F,
                        "Left winding changed at rotation " + rotation);
                assertTrue(normal(right).dot(inputNormal) > 0.0F,
                        "Right winding changed at rotation " + rotation);
            }
        }
    }

    @Test
    void computesInterpolationFromClippingSpaceAndReturnsRawSpacePositions() {
        QuadClipper.Quad input = new QuadClipper.Quad(
                vertex(10.0F, -1.0F, 0.0F, 0.0F, 10.0F, -1.0F),
                vertex(14.0F, -1.0F, 0.0F, 8.0F, 18.0F, 3.0F),
                vertex(14.0F, 1.0F, 0.0F, 12.0F, 30.0F, 3.0F),
                vertex(10.0F, 1.0F, 0.0F, 4.0F, 22.0F, -1.0F));

        QuadClipper.Quad left = split(input, QuadClipper.Side.LEFT);

        assertVertex(left.vertex(1), 11.0F, -1.0F, 0.0F, 2.0F, 12.0F);
        assertVertex(left.vertex(2), 11.0F, 1.0F, 0.0F, 6.0F, 24.0F);
        assertEquals(0.0F, left.vertex(1).clippingPosition().x());
        assertEquals(0.0F, left.vertex(2).clippingPosition().x());
    }

    @Test
    void supportsEveryCyclicStartAndBothValidWindings() {
        List<QuadClipper.Vertex> forward = rectangle(-2.0F, 2.0F, -1.0F, 1.0F).vertices();
        List<QuadClipper.Vertex> reverse = List.of(
                forward.get(0), forward.get(3), forward.get(2), forward.get(1));

        for (List<QuadClipper.Vertex> winding : List.of(forward, reverse)) {
            for (int rotation = 0; rotation < 4; rotation++) {
                List<QuadClipper.Vertex> rotated = rotate(winding, rotation);
                QuadClipper.Quad input = new QuadClipper.Quad(rotated);
                QuadClipper.Point inputNormal = normal(input);
                for (QuadClipper.Side side : QuadClipper.Side.values()) {
                    QuadClipper.Quad output = split(input, side);
                    assertTrue(normal(output).dot(inputNormal) > 0.0F,
                            "Winding changed for " + side + " at rotation " + rotation);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("supportedOrientations")
    void splitsRectanglesOnEverySupportedPlaneAndAffineOrientation(QuadClipper.Quad input) {
        for (QuadClipper.Side side : QuadClipper.Side.values()) {
            QuadClipper.Quad output = split(input, side);
            assertEquals(4, output.vertices().size());
            assertTrue(output.vertices().stream().anyMatch(vertex -> vertex.clippingPosition().x() == 0.0F));
            assertTrue(normal(output).dot(normal(input)) > 0.0F);
        }
    }

    static Stream<QuadClipper.Quad> supportedOrientations() {
        QuadClipper.Quad xy = rectangle(-2.0F, 2.0F, -1.0F, 1.0F);
        QuadClipper.Quad xz = new QuadClipper.Quad(
                vertex(-2.0F, 0.25F, -1.0F),
                vertex(2.0F, 0.25F, -1.0F),
                vertex(2.0F, 0.25F, 1.0F),
                vertex(-2.0F, 0.25F, 1.0F));
        float cosine = (float) Math.cos(0.37D);
        float sine = (float) Math.sin(0.37D);
        QuadClipper.Quad tilted = new QuadClipper.Quad(
                vertex(-2.0F * cosine, -1.0F, -2.0F * sine),
                vertex(2.0F * cosine, -1.0F, 2.0F * sine),
                vertex(2.0F * cosine, 1.0F, 2.0F * sine),
                vertex(-2.0F * cosine, 1.0F, -2.0F * sine));
        QuadClipper.Quad nonuniformScale = rectangle(-3.0F, 3.0F, -0.25F, 0.25F);
        return Stream.of(xy, xz, tilted, nonuniformScale);
    }

    @Test
    void appliesTheEpsilonAtANearEdgeWithoutCreatingASliver() {
        QuadClipper.Quad withinEpsilon = rectangle(-0.5F * EPSILON, 1.0F, -1.0F, 1.0F);
        QuadClipper.Quad beyondEpsilon = rectangle(-2.0F * EPSILON, 1.0F, -1.0F, 1.0F);

        assertDecision(withinEpsilon, QuadClipper.Side.LEFT, QuadClipper.Decision.DROP);
        assertDecision(withinEpsilon, QuadClipper.Side.RIGHT, QuadClipper.Decision.KEEP);
        assertDecision(beyondEpsilon, QuadClipper.Side.LEFT, QuadClipper.Decision.SPLIT);
        assertDecision(beyondEpsilon, QuadClipper.Side.RIGHT, QuadClipper.Decision.SPLIT);
    }

    @Test
    void treatsCutsThroughOppositeVerticesAsUnsupportedTopology() {
        QuadClipper.Quad throughVertices = new QuadClipper.Quad(
                vertex(0.0F, -1.0F, 0.0F),
                vertex(1.0F, 0.0F, 0.0F),
                vertex(0.0F, 1.0F, 0.0F),
                vertex(-1.0F, 0.0F, 0.0F));

        assertIncompatible(throughVertices, QuadClipper.Incompatibility.UNSUPPORTED_TOPOLOGY);
    }

    @Test
    void keepsTheCenterShortcutDisabledForNarrowUnsupportedDetails() {
        QuadClipper.Quad narrowBowTie = new QuadClipper.Quad(
                vertex(-2.0F * EPSILON, -1.0F, 0.0F),
                vertex(2.0F * EPSILON, 1.0F, 0.0F),
                vertex(-2.0F * EPSILON, 1.0F, 0.0F),
                vertex(2.0F * EPSILON, -1.0F, 0.0F));

        assertIncompatible(narrowBowTie, QuadClipper.Incompatibility.NOT_RECTANGLE);
    }

    @Test
    void acceptsMeasuredNoiseAndRejectsLargerNonPlanarity() {
        QuadClipper.Quad accepted = warpedRectangle(0.5F * EPSILON);
        QuadClipper.Quad rejected = warpedRectangle(4.0F * EPSILON);

        assertDecision(accepted, QuadClipper.Side.LEFT, QuadClipper.Decision.SPLIT);
        assertIncompatible(rejected, QuadClipper.Incompatibility.NOT_RECTANGLE);
    }

    @ParameterizedTest
    @MethodSource("invalidCrossingShapes")
    void rejectsFiniteButUnsupportedCrossingGeometry(
            QuadClipper.Quad input,
            QuadClipper.Incompatibility expectedReason) {
        assertIncompatible(input, expectedReason);
    }

    static Stream<Arguments> invalidCrossingShapes() {
        return Stream.of(
                Arguments.of(new QuadClipper.Quad(
                                vertex(-1.0F, -1.0F, 0.0F),
                                vertex(1.0F, -1.0F, 0.0F),
                                vertex(0.5F, 1.0F, 0.0F),
                                vertex(-1.0F, 1.0F, 0.0F)),
                        QuadClipper.Incompatibility.NOT_RECTANGLE),
                Arguments.of(new QuadClipper.Quad(
                                vertex(-1.0F, -1.0F, 0.0F),
                                vertex(1.0F, -1.0F, 0.0F),
                                vertex(-0.25F, 0.0F, 0.0F),
                                vertex(1.0F, 1.0F, 0.0F)),
                        QuadClipper.Incompatibility.NOT_RECTANGLE),
                Arguments.of(new QuadClipper.Quad(
                                vertex(-1.0F, -1.0F, 0.0F),
                                vertex(1.0F, 1.0F, 0.0F),
                                vertex(-1.0F, 1.0F, 0.0F),
                                vertex(1.0F, -1.0F, 0.0F)),
                        QuadClipper.Incompatibility.NOT_RECTANGLE),
                Arguments.of(warpedRectangle(0.01F), QuadClipper.Incompatibility.NOT_RECTANGLE),
                Arguments.of(new QuadClipper.Quad(
                                vertex(-1.0F, -1.0F, 0.0F),
                                vertex(1.0F, -1.0F, 0.0F),
                                vertex(1.0F, -1.0F, 0.0F),
                                vertex(-1.0F, 1.0F, 0.0F)),
                        QuadClipper.Incompatibility.NOT_RECTANGLE),
                Arguments.of(rotatedAndTranslatedRectangle(),
                        QuadClipper.Incompatibility.UNSUPPORTED_TOPOLOGY));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteQuads")
    void rejectsNonFinitePositionsClippingCoordinatesAndUvs(QuadClipper.Quad input) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> QuadClipper.clip(input, QuadClipper.Side.LEFT));
        assertTrue(exception.getMessage().contains("non-finite"));
    }

    static Stream<QuadClipper.Quad> nonFiniteQuads() {
        return Stream.of(
                replacingVertex(rectangle(-1.0F, 1.0F, -1.0F, 1.0F), 0,
                        new QuadClipper.Vertex(
                                new QuadClipper.Point(Float.NaN, 0.0F, 0.0F),
                                new QuadClipper.Uv(0.0F, 0.0F),
                                new QuadClipper.Point(-1.0F, -1.0F, 0.0F))),
                replacingVertex(rectangle(-1.0F, 1.0F, -1.0F, 1.0F), 0,
                        new QuadClipper.Vertex(
                                new QuadClipper.Point(-1.0F, -1.0F, 0.0F),
                                new QuadClipper.Uv(0.0F, 0.0F),
                                new QuadClipper.Point(Float.POSITIVE_INFINITY, 0.0F, 0.0F))),
                replacingVertex(rectangle(-1.0F, 1.0F, -1.0F, 1.0F), 0,
                        new QuadClipper.Vertex(
                                new QuadClipper.Point(-1.0F, -1.0F, 0.0F),
                                new QuadClipper.Uv(0.0F, Float.NEGATIVE_INFINITY),
                                new QuadClipper.Point(-1.0F, -1.0F, 0.0F))));
    }

    @Test
    void isDeterministicAndDoesNotDependOnInputIdentity() {
        QuadClipper.Quad firstInput = rectangle(-2.0F, 2.0F, -1.0F, 1.0F);
        QuadClipper.Quad equalInput = copy(firstInput);

        QuadClipper.Result first = QuadClipper.clip(firstInput, QuadClipper.Side.LEFT);
        QuadClipper.Result repeated = QuadClipper.clip(firstInput, QuadClipper.Side.LEFT);
        QuadClipper.Result equalIdentity = QuadClipper.clip(equalInput, QuadClipper.Side.LEFT);

        assertEquals(first, repeated);
        assertEquals(first, equalIdentity);
        assertNotSame(firstInput, equalInput);
        assertEquals(rectangle(-2.0F, 2.0F, -1.0F, 1.0F), firstInput);
    }

    @Test
    void polygonClippingIsDeterministicAndDoesNotMutateItsInput() {
        QuadClipper.Quad firstInput = adjacentEdgeRectangle();
        QuadClipper.Quad equalInput = copy(firstInput);

        QuadClipper.PolygonResult first = QuadClipper.clipPolygon(firstInput, QuadClipper.Side.RIGHT);
        QuadClipper.PolygonResult repeated = QuadClipper.clipPolygon(firstInput, QuadClipper.Side.RIGHT);
        QuadClipper.PolygonResult equalIdentity = QuadClipper.clipPolygon(equalInput, QuadClipper.Side.RIGHT);

        assertEquals(first, repeated);
        assertEquals(first, equalIdentity);
        assertNotSame(firstInput, equalInput);
        assertEquals(adjacentEdgeRectangle(), firstInput);
    }

    @Test
    void validatesQuadAndResultShapesAtTheirBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.Quad(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.Polygon(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.Polygon(List.of(
                vertex(0.0F, 0.0F, 0.0F),
                vertex(1.0F, 0.0F, 0.0F),
                vertex(1.0F, 1.0F, 0.0F),
                vertex(0.0F, 1.0F, 0.0F),
                vertex(-1.0F, 1.0F, 0.0F),
                vertex(-1.0F, 0.0F, 0.0F))));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.Result(
                QuadClipper.Decision.SPLIT, null, null));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.Result(
                QuadClipper.Decision.INCOMPATIBLE,
                rectangle(-1.0F, 1.0F, -1.0F, 1.0F),
                QuadClipper.Incompatibility.NOT_RECTANGLE));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.PolygonResult(
                QuadClipper.Decision.SPLIT, null, null));
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.PolygonResult(
                QuadClipper.Decision.INCOMPATIBLE,
                new QuadClipper.Polygon(adjacentEdgeRectangle().vertices()),
                QuadClipper.Incompatibility.NOT_RECTANGLE));
    }

    private static QuadClipper.Quad rotatedAndTranslatedRectangle() {
        float angle = (float) (Math.PI / 4.0D);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        List<QuadClipper.Vertex> vertices = new ArrayList<>();
        for (QuadClipper.Vertex vertex : rectangle(-1.0F, 1.0F, -1.0F, 1.0F).vertices()) {
            float x = vertex.clippingPosition().x();
            float y = vertex.clippingPosition().y();
            vertices.add(vertex(x * cosine - y * sine + 0.9F,
                    x * sine + y * cosine, 0.0F));
        }
        return new QuadClipper.Quad(vertices);
    }

    private static QuadClipper.Quad adjacentEdgeRectangle() {
        return new QuadClipper.Quad(
                vertex(0.5F, -1.0F, 0.0F),
                vertex(1.5F, 0.0F, 0.0F),
                vertex(0.5F, 1.0F, 0.0F),
                vertex(-0.5F, 0.0F, 0.0F));
    }

    private static QuadClipper.Quad warpedRectangle(float deviation) {
        return new QuadClipper.Quad(
                vertex(-1.0F, -1.0F, 0.0F),
                vertex(1.0F, -1.0F, 0.0F),
                vertex(1.0F, 1.0F, 0.0F),
                vertex(-1.0F, 1.0F, deviation));
    }

    private static QuadClipper.Quad replacingVertex(
            QuadClipper.Quad input, int index, QuadClipper.Vertex replacement) {
        List<QuadClipper.Vertex> vertices = new ArrayList<>(input.vertices());
        vertices.set(index, replacement);
        return new QuadClipper.Quad(vertices);
    }

    private static QuadClipper.Quad copy(QuadClipper.Quad input) {
        return new QuadClipper.Quad(input.vertices().stream()
                .map(vertex -> new QuadClipper.Vertex(
                        new QuadClipper.Point(
                                vertex.position().x(), vertex.position().y(), vertex.position().z()),
                        new QuadClipper.Uv(vertex.uv().u(), vertex.uv().v()),
                        new QuadClipper.Point(
                                vertex.clippingPosition().x(),
                                vertex.clippingPosition().y(),
                                vertex.clippingPosition().z())))
                .toList());
    }

    private static List<QuadClipper.Vertex> rotate(List<QuadClipper.Vertex> input, int distance) {
        List<QuadClipper.Vertex> result = new ArrayList<>(input);
        Collections.rotate(result, -distance);
        return result;
    }

    private static QuadClipper.Point normal(QuadClipper.Quad quad) {
        return quad.vertex(1).clippingPosition().subtract(quad.vertex(0).clippingPosition())
                .cross(quad.vertex(2).clippingPosition().subtract(quad.vertex(1).clippingPosition()));
    }

    private static QuadClipper.Point normal(QuadClipper.Polygon polygon) {
        QuadClipper.Point origin = polygon.vertex(0).clippingPosition();
        for (int index = 1; index < polygon.vertices().size() - 1; index++) {
            QuadClipper.Point result = polygon.vertex(index).clippingPosition().subtract(origin)
                    .cross(polygon.vertex(index + 1).clippingPosition().subtract(origin));
            if (result.lengthSquared() > EPSILON * EPSILON) {
                return result;
            }
        }
        throw new AssertionError("Polygon has no measurable normal");
    }

    private static QuadClipper.Quad split(QuadClipper.Quad input, QuadClipper.Side side) {
        QuadClipper.Result result = QuadClipper.clip(input, side);
        assertEquals(QuadClipper.Decision.SPLIT, result.decision());
        assertNull(result.incompatibility());
        return result.quad();
    }

    private static QuadClipper.Polygon splitPolygon(QuadClipper.Quad input, QuadClipper.Side side) {
        QuadClipper.PolygonResult result = QuadClipper.clipPolygon(input, side);
        assertEquals(QuadClipper.Decision.SPLIT, result.decision());
        assertNull(result.incompatibility());
        return result.polygon();
    }

    private static void assertDecision(
            QuadClipper.Quad input,
            QuadClipper.Side side,
            QuadClipper.Decision decision) {
        QuadClipper.Result result = QuadClipper.clip(input, side);
        assertEquals(decision, result.decision());
        if (decision != QuadClipper.Decision.SPLIT) {
            assertNull(result.quad());
        }
        assertNull(result.incompatibility());
    }

    private static void assertIncompatible(
            QuadClipper.Quad input, QuadClipper.Incompatibility incompatibility) {
        for (QuadClipper.Side side : QuadClipper.Side.values()) {
            QuadClipper.Result result = QuadClipper.clip(input, side);
            assertEquals(QuadClipper.Decision.INCOMPATIBLE, result.decision());
            assertEquals(incompatibility, result.incompatibility());
            assertNull(result.quad());
        }
    }

    private static QuadClipper.Quad rectangle(float minX, float maxX, float minY, float maxY) {
        return new QuadClipper.Quad(
                vertex(minX, minY, 0.0F),
                vertex(maxX, minY, 0.0F),
                vertex(maxX, maxY, 0.0F),
                vertex(minX, maxY, 0.0F));
    }

    private static QuadClipper.Vertex vertex(float x, float y, float z) {
        return vertex(x, y, z, x + 4.0F, y + 8.0F);
    }

    private static QuadClipper.Vertex vertex(float x, float y, float z, float u, float v) {
        return vertex(x, y, z, u, v, x);
    }

    private static QuadClipper.Vertex vertex(
            float x, float y, float z, float u, float v, float clippingX) {
        return new QuadClipper.Vertex(
                new QuadClipper.Point(x, y, z),
                new QuadClipper.Uv(u, v),
                new QuadClipper.Point(clippingX, y, z));
    }

    private static void assertVertex(
            QuadClipper.Vertex actual,
            float x,
            float y,
            float z,
            float u,
            float v) {
        assertEquals(x, actual.position().x(), EPSILON);
        assertEquals(y, actual.position().y(), EPSILON);
        assertEquals(z, actual.position().z(), EPSILON);
        assertEquals(u, actual.uv().u(), EPSILON);
        assertEquals(v, actual.uv().v(), EPSILON);
    }
}
