package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;

import java.util.ArrayList;
import java.util.List;

public final class GuiItemSubmissionProbe {
    private static int expectedX;
    private static int expectedY;
    private static int expectedSubmissions;
    private static boolean armed;
    private static final List<GuiItemRenderState> submissions = new ArrayList<>();

    private GuiItemSubmissionProbe() {
    }

    public static void arm(int x, int y) {
        arm(x, y, 2);
    }

    public static void arm(int x, int y, int count) {
        expectedX = x;
        expectedY = y;
        expectedSubmissions = count;
        submissions.clear();
        armed = true;
    }

    public static void record(GuiItemRenderState state) {
        if (armed
                && state.x() == expectedX
                && state.y() == expectedY
                && submissions.size() < expectedSubmissions) {
            submissions.add(state);
            if (submissions.size() == expectedSubmissions) {
                armed = false;
            }
        }
    }

    public static boolean isComplete() {
        return expectedSubmissions > 0 && submissions.size() == expectedSubmissions;
    }

    public static List<GuiItemRenderState> take() {
        List<GuiItemRenderState> result = List.copyOf(submissions);
        submissions.clear();
        expectedSubmissions = 0;
        armed = false;
        return result;
    }
}
