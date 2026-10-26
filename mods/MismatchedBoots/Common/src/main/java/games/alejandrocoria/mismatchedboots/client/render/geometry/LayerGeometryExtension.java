package games.alejandrocoria.mismatchedboots.client.render.geometry;

import org.joml.Matrix4fc;

import java.util.Objects;

public interface LayerGeometryExtension {
    Plan mismatchedboots$planGeometry(Matrix4fc localTransform, QuadClipper.Side side);

    record Plan(boolean compatible, Runnable commit, boolean animated) {
        public static final Plan NONE = new Plan(true, () -> {}, false);
        public static final Plan INCOMPATIBLE = new Plan(false, () -> {}, false);

        public Plan {
            Objects.requireNonNull(commit, "commit");
        }
    }
}
