package games.alejandrocoria.leggingsspike;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Local-space proof, deliberately independent of Boots and baked-item conventions. */
public final class WaistClipper {
    private static final float EPSILON = 0.00001F;

    private WaistClipper() {
    }

    public static List<ModelPart.Cube> clip(ModelPart.Cube cube, boolean anatomicalLeft) {
        return clip(cube, anatomicalLeft, new Matrix4f());
    }

    public static List<ModelPart.Cube> clip(ModelPart.Cube cube, boolean anatomicalLeft, Matrix4fc localToWaist) {
        if (cube.getClass() != ModelPart.Cube.class) {
            throw new IllegalArgumentException("Custom cube compiler is outside this spike");
        }
        if (!localToWaist.isFinite() || Math.abs(localToWaist.determinant()) < 0.00000001F) {
            throw new IllegalArgumentException("Invalid reference waist transform");
        }

        List<ModelPart.Cube> result = new ArrayList<>();
        for (ModelPart.Polygon polygon : cube.polygons) {
            List<ModelPart.Vertex> vertices = clipFace(polygon, anatomicalLeft, localToWaist);
            if (vertices.size() < 3) {
                continue;
            }
            if (vertices.size() == 4) {
                result.add(ownedFace(vertices.toArray(ModelPart.Vertex[]::new), polygon));
            } else {
                for (int i = 1; i + 1 < vertices.size(); i++) {
                    result.add(ownedFace(new ModelPart.Vertex[]{vertices.getFirst(), vertices.get(i),
                            vertices.get(i + 1), vertices.get(i + 1)}, polygon));
                }
            }
        }
        return result;
    }

    private static List<ModelPart.Vertex> clipFace(ModelPart.Polygon polygon, boolean left, Matrix4fc localToWaist) {
        ModelPart.Vertex[] input = polygon.vertices();
        if (input.length != 4 || !polygon.normal().isFinite()) {
            throw new IllegalArgumentException("Unsupported waist face");
        }
        boolean centerOnly = true;
        for (ModelPart.Vertex vertex : input) {
            if (!Float.isFinite(vertex.x()) || !Float.isFinite(vertex.y()) || !Float.isFinite(vertex.z())
                    || !Float.isFinite(vertex.u()) || !Float.isFinite(vertex.v())) {
                throw new IllegalArgumentException("Nonfinite waist vertex");
            }
            centerOnly &= distance(vertex, localToWaist) == 0.0F;
        }
        if (centerOnly) {
            return left ? List.of(input.clone()) : List.of();
        }

        List<ModelPart.Vertex> output = new ArrayList<>(5);
        ModelPart.Vertex previous = input[input.length - 1];
        float previousDistance = distance(previous, localToWaist);
        boolean previousInside = inside(previousDistance, left);
        for (ModelPart.Vertex current : input) {
            float currentDistance = distance(current, localToWaist);
            boolean currentInside = inside(currentDistance, left);
            if (previousInside != currentInside) {
                float t = previousDistance / (previousDistance - currentDistance);
                append(output, new ModelPart.Vertex(mix(previous.x(), current.x(), t),
                        mix(previous.y(), current.y(), t), mix(previous.z(), current.z(), t),
                        mix(previous.u(), current.u(), t), mix(previous.v(), current.v(), t)));
            }
            if (currentInside) {
                append(output, new ModelPart.Vertex(current.x(), current.y(), current.z(), current.u(), current.v()));
            }
            previous = current;
            previousDistance = currentDistance;
            previousInside = currentInside;
        }
        if (output.size() > 1 && samePosition(output.getFirst(), output.getLast())) {
            output.removeLast();
        }
        return output;
    }

    private static float distance(ModelPart.Vertex vertex, Matrix4fc localToWaist) {
        float x = localToWaist.transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f()).x;
        if (!Float.isFinite(x)) {
            throw new IllegalArgumentException("Nonfinite waist clipping coordinate");
        }
        return Math.abs(x) < EPSILON ? 0.0F : x;
    }

    private static boolean inside(float distance, boolean left) {
        return left ? distance >= 0.0F : distance <= 0.0F;
    }

    private static void append(List<ModelPart.Vertex> output, ModelPart.Vertex vertex) {
        if (output.isEmpty() || !samePosition(output.getLast(), vertex)) {
            output.add(vertex);
        }
    }

    private static boolean samePosition(ModelPart.Vertex a, ModelPart.Vertex b) {
        return Math.abs(a.x() - b.x()) < EPSILON && Math.abs(a.y() - b.y()) < EPSILON
                && Math.abs(a.z() - b.z()) < EPSILON;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static ModelPart.Cube ownedFace(ModelPart.Vertex[] vertices, ModelPart.Polygon source) {
        // The dummy box allocates one privately owned polygon slot. Native compile reads
        // that polygon, not the undeformed bounds. No source cube/array is edited.
        ModelPart.Cube result = new ModelPart.Cube(0, 0, 0, 0, 0, 1, 1, 1,
                0, 0, 0, false, 64, 32, Set.of(Direction.UP));
        result.polygons[0] = new ModelPart.Polygon(vertices, new Vector3f(source.normal()));
        return result;
    }
}
