package games.alejandrocoria.mismatchedleggings.client.render;

import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Render-thread cache, scoped to a resource epoch and bounded even for fresh-model providers. */
public final class LeggingsGeometryCache {
    static final int MAX_GEOMETRIES = 128;
    private static final Map<ModelPart, WaistGeometry[]> GEOMETRY = new IdentityHashMap<>();
    private static final ArrayDeque<ModelPart> ORDER = new ArrayDeque<>();
    private static long builds;
    private static long hits;
    private static long generation;

    private LeggingsGeometryCache() {}

    static WaistGeometry geometry(ModelPart waist, boolean left) {
        var sides = GEOMETRY.get(waist);
        int side = left ? 0 : 1;
        if (sides != null && sides[side] != null) {
            hits++;
            return sides[side];
        }
        // Build before retaining the key: unsupported geometry never poisons an entry.
        var geometry = new WaistGeometry(waist, left);
        builds++;
        if (sides == null) {
            if (GEOMETRY.size() == MAX_GEOMETRIES) {
                GEOMETRY.remove(ORDER.removeFirst());
            }
            sides = new WaistGeometry[2];
            GEOMETRY.put(waist, sides);
            ORDER.addLast(waist);
        }
        sides[side] = geometry;
        return geometry;
    }

    public static Statistics statistics() {
        return new Statistics(GEOMETRY.size(), builds, hits, generation);
    }

    public record Statistics(int geometries, long builds, long hits, long generation) {}

    public static void clear() {
        GEOMETRY.clear();
        ORDER.clear();
        generation++;
    }
}
