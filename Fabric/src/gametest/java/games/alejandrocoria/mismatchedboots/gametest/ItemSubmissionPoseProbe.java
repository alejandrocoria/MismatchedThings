package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;

public final class ItemSubmissionPoseProbe {
    private static List<BakedQuad> targetQuads;
    private static Submission submission;

    private ItemSubmissionPoseProbe() {
    }

    public static void arm(List<BakedQuad> quads) {
        targetQuads = quads;
        submission = null;
    }

    public static void record(ItemDisplayContext displayContext, Matrix4fc pose, List<BakedQuad> quads) {
        if (submission == null && quads == targetQuads) {
            submission = new Submission(displayContext, new Matrix4f(pose));
        }
    }

    public static Submission take() {
        Submission result = submission;
        targetQuads = null;
        submission = null;
        if (result == null) {
            throw new AssertionError("The expected item layer was not submitted");
        }
        return result;
    }

    public static void takeIfArmed() {
        targetQuads = null;
        submission = null;
    }

    public record Submission(ItemDisplayContext displayContext, Matrix4fc pose) {
    }
}
