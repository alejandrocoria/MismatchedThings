package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ItemLayerResolutionProbe {
    public static final Identifier NONPLANAR_EXTENSION_MODEL = Identifier.fromNamespaceAndPath(
            "mismatchedboots-test", "geometry_extension_nonplanar");

    private static Set<Identifier> targets = Set.of();
    private static final Map<Identifier, Integer> counts = new HashMap<>();

    private ItemLayerResolutionProbe() {
    }

    public static void arm(Set<Identifier> modelIds) {
        targets = Set.copyOf(modelIds);
        counts.clear();
    }

    public static void record(Identifier modelId) {
        if (targets.contains(modelId)) {
            counts.merge(modelId, 1, Integer::sum);
        }
    }

    public static int count(Identifier modelId) {
        return counts.getOrDefault(modelId, 0);
    }

    public static boolean requiresNonplanarExtensionGeometry(Identifier modelId) {
        return NONPLANAR_EXTENSION_MODEL.equals(modelId);
    }

    public static void clear() {
        targets = Set.of();
        counts.clear();
    }
}
