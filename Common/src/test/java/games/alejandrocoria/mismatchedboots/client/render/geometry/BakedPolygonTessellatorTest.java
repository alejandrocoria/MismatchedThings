package games.alejandrocoria.mismatchedboots.client.render.geometry;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedPolygonTessellatorTest {
    private static final float EPSILON = QuadClipper.GEOMETRY_EPSILON;

    @Test
    void encodesTriangleByRepeatingItsFinalVertex() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            QuadClipper.Polygon polygon = polygon(
                    vertex(0.0F, 0.0F, 2.0F, 4.0F),
                    vertex(2.0F, 0.0F, 10.0F, 8.0F),
                    vertex(0.0F, 2.0F, 6.0F, 16.0F));

            List<BakedQuad> output = BakedPolygonTessellator.tessellate(source, polygon);

            assertEquals(1, output.size());
            BakedQuad triangle = output.getFirst();
            assertVertex(triangle, 0, 0.0F, 0.0F, 2.0F, 4.0F);
            assertVertex(triangle, 1, 2.0F, 0.0F, 10.0F, 8.0F);
            assertVertex(triangle, 2, 0.0F, 2.0F, 6.0F, 16.0F);
            assertVertex(triangle, 3, 0.0F, 2.0F, 6.0F, 16.0F);
            assertMetadata(source, triangle);
            assertEquals(area(polygon), renderedArea(output), EPSILON);
            assertThrows(UnsupportedOperationException.class, () -> output.add(source));
        }
    }

    @Test
    void preservesFourVertexPolygonAsOneOrdinaryQuad() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            QuadClipper.Polygon polygon = polygon(
                    vertex(0.0F, 0.0F, 1.0F, 2.0F),
                    vertex(2.0F, 0.0F, 3.0F, 4.0F),
                    vertex(2.0F, 2.0F, 5.0F, 6.0F),
                    vertex(0.0F, 2.0F, 7.0F, 8.0F));

            List<BakedQuad> output = BakedPolygonTessellator.tessellate(source, polygon);

            assertEquals(1, output.size());
            for (int index = 0; index < 4; index++) {
                assertVertex(output.getFirst(), index, polygon.vertex(index));
            }
            assertMetadata(source, output.getFirst());
            assertEquals(area(polygon), renderedArea(output), EPSILON);
        }
    }

    @Test
    void partitionsPentagonIntoQuadThenFinalTriangleWithoutOverlap() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            QuadClipper.Polygon polygon = pentagon();

            List<BakedQuad> output = BakedPolygonTessellator.tessellate(source, polygon);

            assertEquals(2, output.size());
            BakedQuad quad = output.get(0);
            BakedQuad triangle = output.get(1);
            assertVertex(quad, 0, polygon.vertex(0));
            assertVertex(quad, 1, polygon.vertex(1));
            assertVertex(quad, 2, polygon.vertex(2));
            assertVertex(quad, 3, polygon.vertex(3));
            assertVertex(triangle, 0, polygon.vertex(0));
            assertVertex(triangle, 1, polygon.vertex(3));
            assertVertex(triangle, 2, polygon.vertex(4));
            assertVertex(triangle, 3, polygon.vertex(4));
            assertMetadata(source, quad);
            assertMetadata(source, triangle);
            assertEquals(area(polygon), renderedArea(output), EPSILON);
        }
    }

    @Test
    void preservesCoverageAndWindingForCyclicStartsAndBothWindings() {
        List<QuadClipper.Vertex> forward = pentagon().vertices();
        List<QuadClipper.Vertex> reverse = List.of(
                forward.get(0), forward.get(4), forward.get(3), forward.get(2), forward.get(1));
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            for (List<QuadClipper.Vertex> winding : List.of(forward, reverse)) {
                for (int rotation = 0; rotation < 5; rotation++) {
                    List<QuadClipper.Vertex> vertices = new ArrayList<>(winding);
                    Collections.rotate(vertices, -rotation);
                    QuadClipper.Polygon polygon = new QuadClipper.Polygon(vertices);
                    List<BakedQuad> output = BakedPolygonTessellator.tessellate(source, polygon);

                    assertEquals(2, output.size());
                    assertEquals(area(polygon), renderedArea(output), EPSILON);
                    Vector3f expectedNormal = normal(polygon);
                    for (BakedQuad quad : output) {
                        assertTrue(normal(quad).dot(expectedNormal) > 0.0F,
                                "Winding changed at rotation " + rotation);
                    }
                }
            }
        }
    }

    @Test
    void isDeterministicAcrossEqualSourceAndPolygonIdentities() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad firstSource = source(fixture.material());
            BakedQuad equalSource = source(fixture.material());
            QuadClipper.Polygon firstPolygon = pentagon();
            QuadClipper.Polygon equalPolygon = new QuadClipper.Polygon(firstPolygon.vertices().stream()
                    .map(BakedPolygonTessellatorTest::copy)
                    .toList());

            List<BakedQuad> first = BakedPolygonTessellator.tessellate(firstSource, firstPolygon);
            List<BakedQuad> repeated = BakedPolygonTessellator.tessellate(firstSource, firstPolygon);
            List<BakedQuad> equalIdentity = BakedPolygonTessellator.tessellate(equalSource, equalPolygon);

            assertEquals(first, repeated);
            assertEquals(first, equalIdentity);
            assertEquals(2, first.size());
        }
    }

    @Test
    void rejectsNonFiniteDegenerateNonPlanarConcaveAndSelfIntersectingPolygons() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            List<QuadClipper.Polygon> invalid = List.of(
                    polygon(
                            vertex(Float.NaN, 0.0F, 0.0F, 0.0F),
                            vertex(1.0F, 0.0F, 1.0F, 0.0F),
                            vertex(0.0F, 1.0F, 0.0F, 1.0F)),
                    polygon(
                            vertex(0.0F, 0.0F, 0.0F, 0.0F),
                            vertex(1.0F, 0.0F, 1.0F, 0.0F),
                            vertex(1.0F, 0.0F, 1.0F, 0.0F)),
                    polygon(
                            vertex(0.0F, 0.0F, 0.0F, 0.0F),
                            vertex(1.0F, 0.0F, 1.0F, 0.0F),
                            vertex(1.0F, 1.0F, 1.0F, 1.0F),
                            vertex(0.0F, 1.0F, 0.0F, 1.0F, 0.01F)),
                    polygon(
                            vertex(0.0F, 0.0F, 0.0F, 0.0F),
                            vertex(2.0F, 0.0F, 2.0F, 0.0F),
                            vertex(1.0F, 0.5F, 1.0F, 0.5F),
                            vertex(2.0F, 2.0F, 2.0F, 2.0F),
                            vertex(0.0F, 2.0F, 0.0F, 2.0F)),
                    polygon(
                            vertex(0.0F, 0.0F, 0.0F, 0.0F),
                            vertex(2.0F, 2.0F, 2.0F, 2.0F),
                            vertex(0.0F, 2.0F, 0.0F, 2.0F),
                            vertex(2.0F, 0.0F, 2.0F, 0.0F)));

            for (QuadClipper.Polygon polygon : invalid) {
                assertThrows(IllegalArgumentException.class,
                        () -> BakedPolygonTessellator.tessellate(source, polygon));
            }
        }
    }

    @Test
    void rejectsMissingSourceMetadata() {
        try (BakedQuadClipperTest.MaterialFixture fixture =
                     BakedQuadClipperTest.MaterialFixture.animatedTranslucent()) {
            BakedQuad source = source(fixture.material());
            BakedQuad missingDirection = new BakedQuad(
                    source.position0(), source.position1(), source.position2(), source.position3(),
                    source.packedUV0(), source.packedUV1(), source.packedUV2(), source.packedUV3(),
                    null, source.materialInfo());
            BakedQuad missingMaterial = new BakedQuad(
                    source.position0(), source.position1(), source.position2(), source.position3(),
                    source.packedUV0(), source.packedUV1(), source.packedUV2(), source.packedUV3(),
                    source.direction(), null);

            assertThrows(IllegalArgumentException.class,
                    () -> BakedPolygonTessellator.tessellate(missingDirection, pentagon()));
            assertThrows(IllegalArgumentException.class,
                    () -> BakedPolygonTessellator.tessellate(missingMaterial, pentagon()));
        }
    }

    private static QuadClipper.Polygon pentagon() {
        return polygon(
                vertex(0.0F, 0.0F, 2.0F, 4.0F),
                vertex(2.0F, 0.0F, 10.0F, 8.0F),
                vertex(3.0F, 1.0F, 14.0F, 12.0F),
                vertex(1.5F, 2.0F, 10.0F, 18.0F),
                vertex(0.0F, 1.0F, 4.0F, 14.0F));
    }

    private static QuadClipper.Polygon polygon(QuadClipper.Vertex... vertices) {
        return new QuadClipper.Polygon(List.of(vertices));
    }

    private static QuadClipper.Vertex vertex(float x, float y, float u, float v) {
        return vertex(x, y, u, v, 0.0F);
    }

    private static QuadClipper.Vertex vertex(float x, float y, float u, float v, float z) {
        return new QuadClipper.Vertex(
                new QuadClipper.Point(x, y, z),
                new QuadClipper.Uv(u, v),
                new QuadClipper.Point(x, y, z));
    }

    private static QuadClipper.Vertex copy(QuadClipper.Vertex vertex) {
        return new QuadClipper.Vertex(
                new QuadClipper.Point(
                        vertex.position().x(), vertex.position().y(), vertex.position().z()),
                new QuadClipper.Uv(vertex.uv().u(), vertex.uv().v()),
                new QuadClipper.Point(
                        vertex.clippingPosition().x(),
                        vertex.clippingPosition().y(),
                        vertex.clippingPosition().z()));
    }

    private static BakedQuad source(BakedQuad.MaterialInfo material) {
        return new BakedQuad(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                UVPair.pack(0.0F, 0.0F),
                UVPair.pack(1.0F, 0.0F),
                UVPair.pack(1.0F, 1.0F),
                UVPair.pack(0.0F, 1.0F),
                Direction.SOUTH,
                material);
    }

    private static void assertMetadata(BakedQuad source, BakedQuad output) {
        assertEquals(source.direction(), output.direction());
        assertSame(source.materialInfo(), output.materialInfo());
    }

    private static void assertVertex(BakedQuad quad, int index, QuadClipper.Vertex vertex) {
        assertVertex(quad, index,
                vertex.position().x(), vertex.position().y(), vertex.uv().u(), vertex.uv().v());
        assertEquals(vertex.position().z(), quad.position(index).z(), EPSILON);
    }

    private static void assertVertex(BakedQuad quad, int index, float x, float y, float u, float v) {
        assertEquals(x, quad.position(index).x(), EPSILON);
        assertEquals(y, quad.position(index).y(), EPSILON);
        assertEquals(u, UVPair.unpackU(quad.packedUV(index)), EPSILON);
        assertEquals(v, UVPair.unpackV(quad.packedUV(index)), EPSILON);
    }

    private static float area(QuadClipper.Polygon polygon) {
        float result = 0.0F;
        for (int index = 1; index < polygon.vertices().size() - 1; index++) {
            result += triangleArea(
                    vector(polygon.vertex(0).position()),
                    vector(polygon.vertex(index).position()),
                    vector(polygon.vertex(index + 1).position()));
        }
        return result;
    }

    private static float renderedArea(List<BakedQuad> quads) {
        float result = 0.0F;
        for (BakedQuad quad : quads) {
            result += triangleArea(quad.position0(), quad.position1(), quad.position2());
            result += triangleArea(quad.position0(), quad.position2(), quad.position3());
        }
        return result;
    }

    private static float triangleArea(Vector3fc first, Vector3fc second, Vector3fc third) {
        return new Vector3f(second).sub(first).cross(new Vector3f(third).sub(first)).length() * 0.5F;
    }

    private static Vector3f normal(QuadClipper.Polygon polygon) {
        return vector(polygon.vertex(1).position()).sub(vector(polygon.vertex(0).position()))
                .cross(vector(polygon.vertex(2).position()).sub(vector(polygon.vertex(1).position())));
    }

    private static Vector3f normal(BakedQuad quad) {
        return new Vector3f(quad.position1()).sub(quad.position0())
                .cross(new Vector3f(quad.position2()).sub(quad.position1()));
    }

    private static Vector3f vector(QuadClipper.Point point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }
}
