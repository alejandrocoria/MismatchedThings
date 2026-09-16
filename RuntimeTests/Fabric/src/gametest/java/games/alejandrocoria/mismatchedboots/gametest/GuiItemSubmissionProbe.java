package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GuiItemSubmissionProbe {
    private static int expectedSubmissions;
    private static int expectedDecorations;
    private static boolean armed;
    private static int[] targetCoordinates = new int[0];
    private static final List<GuiItemRenderState> submissions = new ArrayList<>();
    private static final List<Decoration> decorations = new ArrayList<>();
    private static final List<EventType> events = new ArrayList<>();

    private GuiItemSubmissionProbe() {
    }

    public static void arm(int x, int y) {
        arm(x, y, 1);
    }

    public static void arm(int x, int y, int count) {
        armTargets(count, 0, x, y);
    }

    public static void armTargets(int submissionCount, int decorationCount, int... coordinates) {
        if (coordinates.length == 0 || coordinates.length % 2 != 0) {
            throw new IllegalArgumentException("Target coordinates must contain X/Y pairs");
        }
        targetCoordinates = coordinates.clone();
        expectedSubmissions = submissionCount;
        expectedDecorations = decorationCount;
        submissions.clear();
        decorations.clear();
        events.clear();
        armed = true;
    }

    public static void record(GuiItemRenderState state) {
        if (armed && matchesTarget(state.x(), state.y()) && submissions.size() < expectedSubmissions) {
            submissions.add(state);
            events.add(EventType.ITEM);
            updateArmedState();
        }
    }

    public static void recordDecoration(ItemStack stack, int x, int y, String countText) {
        if (armed && matchesTarget(x, y) && decorations.size() < expectedDecorations) {
            decorations.add(new Decoration(stack.copy(), x, y, countText));
            events.add(EventType.DECORATION);
            updateArmedState();
        }
    }

    private static boolean matchesTarget(int x, int y) {
        for (int index = 0; index < targetCoordinates.length; index += 2) {
            if (targetCoordinates[index] == x && targetCoordinates[index + 1] == y) {
                return true;
            }
        }
        return false;
    }

    private static void updateArmedState() {
        if (isComplete()) {
            armed = false;
        }
    }

    public static boolean isComplete() {
        return expectedSubmissions > 0
                && submissions.size() == expectedSubmissions
                && decorations.size() == expectedDecorations;
    }

    public static Snapshot takeSnapshot() {
        Snapshot result = new Snapshot(
                List.copyOf(submissions), List.copyOf(decorations), List.copyOf(events));
        reset();
        return result;
    }

    private static void reset() {
        targetCoordinates = new int[0];
        submissions.clear();
        decorations.clear();
        events.clear();
        expectedSubmissions = 0;
        expectedDecorations = 0;
        armed = false;
    }

    public static List<GuiItemRenderState> take() {
        return takeSnapshot().submissions();
    }

    public record Decoration(ItemStack stack, int x, int y, String countText) {
    }

    public record Snapshot(
            List<GuiItemRenderState> submissions,
            List<Decoration> decorations,
            List<EventType> events) {
    }

    public enum EventType {
        ITEM,
        DECORATION
    }
}
