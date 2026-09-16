package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RuntimeItemSubmissionProbe {
    private static final int MAX_SUBMISSIONS = 512;

    private static Set<String> targetSprites = Set.of();
    private static final List<Submission> submissions = new ArrayList<>();
    private static boolean armed;
    private static boolean overflow;

    private RuntimeItemSubmissionProbe() {
    }

    public static synchronized void arm(String... spritePathParts) {
        targetSprites = Set.of(spritePathParts);
        submissions.clear();
        overflow = false;
        armed = true;
    }

    public static synchronized void record(
            ItemDisplayContext displayContext,
            Matrix4fc pose,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
        if (!armed || quads.isEmpty()) {
            return;
        }

        String sprite = targetSprites.stream()
                .filter(target -> quads.stream().anyMatch(quad -> quad.materialInfo()
                        .sprite().contents().name().getPath().contains(target)))
                .findFirst()
                .orElse(null);
        if (sprite == null) {
            return;
        }
        if (submissions.size() == MAX_SUBMISSIONS) {
            overflow = true;
            armed = false;
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        int triangleCount = 0;
        for (BakedQuad quad : quads) {
            if (quad.position2().equals(quad.position3())
                    && quad.packedUV2() == quad.packedUV3()) {
                triangleCount++;
            }
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                float x = quad.position(vertex).x();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
        }
        submissions.add(new Submission(
                displayContext,
                sprite,
                minX,
                maxX,
                triangleCount,
                foilType,
                new Matrix4f(pose)));
    }

    public static synchronized boolean hasAtLeast(ItemDisplayContext context, int count) {
        return submissions.stream().filter(submission -> submission.displayContext() == context).count() >= count;
    }

    public static synchronized void recordMesh(
            ItemDisplayContext context, Matrix4fc pose,
            net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView mesh,
            ItemStackRenderState.FoilType foil) {
        if (!armed || mesh.size() == 0) return;
        float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        int[] triangles = {0};
        String[] sprite = {null};
        mesh.forEach(quad -> {
            String path = MeshTestFixtures.sprite(quad.tag());
            if (targetSprites.stream().noneMatch(path::contains)) return;
            sprite[0] = path;
            if (quad.copyPos(2, null).equals(quad.copyPos(3, null))
                    && quad.u(2) == quad.u(3) && quad.v(2) == quad.v(3)) triangles[0]++;
            for (int vertex = 0; vertex < 4; vertex++) {
                bounds[0] = Math.min(bounds[0], quad.x(vertex));
                bounds[1] = Math.max(bounds[1], quad.x(vertex));
            }
        });
        if (sprite[0] == null) return;
        if (submissions.size() >= MAX_SUBMISSIONS) {
            overflow = true;
            armed = false;
            return;
        }
        submissions.add(new Submission(context, sprite[0], bounds[0], bounds[1], triangles[0], foil, new Matrix4f(pose)));
    }

    public static synchronized Snapshot take() {
        Snapshot result = new Snapshot(List.copyOf(submissions), overflow);
        clear();
        return result;
    }

    public static synchronized void clear() {
        targetSprites = Set.of();
        submissions.clear();
        overflow = false;
        armed = false;
    }

    public record Submission(
            ItemDisplayContext displayContext,
            String sprite,
            float minX,
            float maxX,
            int triangleCount,
            ItemStackRenderState.FoilType foilType,
            Matrix4fc pose) {
    }

    public record Snapshot(List<Submission> submissions, boolean overflow) {
    }
}
