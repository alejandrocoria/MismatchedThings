package games.alejandrocoria.mismatchedboots.crossloader.neoforge;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.List;

public final class NeoForgeGeometrySubmissionProbe {
    private static boolean armed;
    private static Submission submission;

    private NeoForgeGeometrySubmissionProbe() {
    }

    public static void arm() {
        if (armed || submission != null) {
            throw new IllegalStateException("Geometry submission probe was already active");
        }
        armed = true;
    }

    public static void record(
            ItemDisplayContext displayContext,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
        if (!armed) {
            return;
        }
        if (submission != null) {
            throw new IllegalStateException("Geometry submission probe recorded more than one item submission");
        }
        submission = new Submission(displayContext, List.copyOf(quads), foilType);
    }

    public static Submission take() {
        if (!armed || submission == null) {
            throw new IllegalStateException("Geometry submission probe did not record an item submission");
        }
        Submission result = submission;
        armed = false;
        submission = null;
        return result;
    }

    public record Submission(
            ItemDisplayContext displayContext,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
    }
}
