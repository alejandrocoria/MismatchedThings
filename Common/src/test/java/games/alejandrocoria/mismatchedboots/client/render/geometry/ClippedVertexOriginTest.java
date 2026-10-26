package games.alejandrocoria.mismatchedboots.client.render.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClippedVertexOriginTest {
    @Test
    void reportsHandAuthoredTriangleAndPentagonOriginsAndSharedPrimitiveIndices() {
        QuadClipper.Quad source = diamond();
        var left = QuadClipper.clipPolygonWithOrigins(source, QuadClipper.Side.LEFT);
        assertEquals(List.of(origin(0, 0, 0), origin(0, 1, 0.5F), origin(3, 0, 0.5F)), left.origins());
        assertEquals(List.of(new PolygonTessellator.Primitive(0, 1, 2, 2)),
                     PolygonTessellator.tessellate(left.geometry().polygon()));
        assertEquals(new QuadClipper.Point(0, -1, 0), left.geometry().polygon().vertex(1).position());
        assertEquals(new QuadClipper.Uv(5, 10), left.geometry().polygon().vertex(1).uv());
        var right = QuadClipper.clipPolygonWithOrigins(source, QuadClipper.Side.RIGHT);
        assertEquals(List.of(origin(0, 1, 0.5F), origin(1, 1, 0), origin(2, 2, 0),
                             origin(3, 3, 0), origin(3, 0, 0.5F)), right.origins());
        assertEquals(List.of(new PolygonTessellator.Primitive(0, 1, 2, 3),
                             new PolygonTessellator.Primitive(0, 3, 4, 4)), PolygonTessellator.tessellate(right.geometry().polygon()));
        assertThrows(UnsupportedOperationException.class, () -> left.origins().clear());
    }

    @Test
    void provenanceMatchesBothWindingsAndCyclicStartsWithoutChangingOldGeometry() {
        for (boolean reverse : List.of(false, true)) {
            for (int rotation = 0; rotation < 4; rotation++) {
                var vertices = new ArrayList<>(diamond().vertices());
                if (reverse) {
                    Collections.reverse(vertices);
                }
                Collections.rotate(vertices, rotation);
                var source = new QuadClipper.Quad(vertices);

                for (QuadClipper.Side side : QuadClipper.Side.values()) {
                    var traced = QuadClipper.clipPolygonWithOrigins(source, side);
                    assertEquals(QuadClipper.clipPolygon(source, side), traced.geometry());

                    for (int index = 0; index < traced.origins().size(); index++) {
                        var origin = traced.origins().get(index);
                        var actual = traced.geometry().polygon().vertex(index);
                        if (origin.retained()) {
                            assertSame(source.vertex(origin.first()), actual);
                        } else {
                            var first = source.vertex(origin.first());
                            var second = source.vertex(origin.second());
                            assertEquals(first.position().lerp(second.position(), origin.factor()), actual.position());
                            assertEquals(first.uv().lerp(second.uv(), origin.factor()), actual.uv());
                        }
                    }
                }
            }
        }
    }

    @Test
    void validatesOriginAndTraceInvariantsAndReportsNoOriginsForKeepDropOrFailure() {
        assertThrows(IllegalArgumentException.class, () -> origin(-1, 0, 0.5F));
        assertThrows(IllegalArgumentException.class, () -> origin(0, 2, 0.5F));
        assertThrows(IllegalArgumentException.class, () -> origin(0, 1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> origin(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> origin(1, 1, 0.5F));
        var centered = new QuadClipper.Quad(List.of(v(0, 0, 0, 0), v(0, 1, 0, 0), v(0, 2, 0, 0), v(0, 3, 0, 0)));
        for (var side : QuadClipper.Side.values()) {
            var result = QuadClipper.clipPolygonWithOrigins(centered, side);
            assertTrue(result.origins().isEmpty());
        }
        var malformed = new QuadClipper.Quad(v(-1, 0, 0, 0), v(1, 0, 0, 0), v(1, 1, 0, 0), v(-1, 2, 0, 0));
        assertEquals(QuadClipper.Decision.INCOMPATIBLE,
                     QuadClipper.clipPolygonWithOrigins(malformed, QuadClipper.Side.LEFT).geometry().decision());
        assertThrows(IllegalArgumentException.class, () -> new QuadClipper.TracedPolygonResult(
                QuadClipper.clipPolygon(diamond(), QuadClipper.Side.LEFT), List.of()));
    }

    private static QuadClipper.VertexOrigin origin(int a, int b, float t) {
        return new QuadClipper.VertexOrigin(a, b, t);
    }

    private static QuadClipper.Quad diamond() {
        return new QuadClipper.Quad(v(-1, 0, 0, 0), v(1, -2, 10, 20), v(3, 0, 20, 40), v(1, 2, 30, 60));
    }

    private static QuadClipper.Vertex v(float x, float y, float u, float v) {
        var point = new QuadClipper.Point(x, y, 0);
        return new QuadClipper.Vertex(point, new QuadClipper.Uv(u, v), point);
    }
}
