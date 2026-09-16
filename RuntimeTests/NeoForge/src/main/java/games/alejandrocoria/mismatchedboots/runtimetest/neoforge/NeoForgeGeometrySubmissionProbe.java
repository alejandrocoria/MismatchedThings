package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.List;

public final class NeoForgeGeometrySubmissionProbe {
    private static boolean armed;
    private static int expected;
    private static final List<Submission> submissions = new ArrayList<>();

    private NeoForgeGeometrySubmissionProbe() {
    }

    public static void arm() {
        arm(1);
    }

    public static void arm(int count) {
        if (armed || !submissions.isEmpty() || count < 1 || count > 2) {
            throw new IllegalStateException("Geometry submission probe was already active");
        }
        armed = true;
        expected = count;
    }

    public static void record(
            ItemDisplayContext displayContext,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
        if (!armed) {
            return;
        }
        if (submissions.size() >= expected) {
            throw new IllegalStateException("Geometry submission probe recorded more than one item submission");
        }
        submissions.add(new Submission(displayContext, List.copyOf(quads), foilType));
    }

    public static Submission take() {
        if (expected != 1) {
            throw new IllegalStateException("Single-submission take used for a composition");
        }
        return takeAll().getFirst();
    }

    public static List<Submission> takeAll() {
        if (!armed || submissions.size() != expected) {
            throw new IllegalStateException("Geometry submission probe did not record an item submission");
        }
        List<Submission> result = List.copyOf(submissions);
        armed = false;
        submissions.clear();
        return result;
    }

    public record Submission(
            ItemDisplayContext displayContext,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
    }
}
