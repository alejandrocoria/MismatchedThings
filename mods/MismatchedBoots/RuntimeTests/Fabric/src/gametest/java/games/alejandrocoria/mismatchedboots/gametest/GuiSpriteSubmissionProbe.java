package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class GuiSpriteSubmissionProbe {
    private static final List<Submission> SUBMISSIONS = new ArrayList<>();
    private static boolean armed;
    private static int frames;

    private GuiSpriteSubmissionProbe() {
    }

    public static synchronized void arm() {
        SUBMISSIONS.clear();
        frames = 0;
        armed = true;
    }

    public static synchronized void recordFrame() {
        if (armed) {
            frames++;
        }
    }

    public static synchronized void record(Identifier sprite, int x, int y, int width, int height) {
        if (armed) {
            SUBMISSIONS.add(new Submission(sprite, x, y, width, height));
        }
    }

    public static synchronized boolean hasFrames(int expected) {
        return frames >= expected;
    }

    public static synchronized Snapshot takeSnapshot() {
        Snapshot snapshot = new Snapshot(frames, List.copyOf(SUBMISSIONS));
        SUBMISSIONS.clear();
        frames = 0;
        armed = false;
        return snapshot;
    }

    public record Submission(Identifier sprite, int x, int y, int width, int height) {
    }

    public record Snapshot(int frames, List<Submission> submissions) {
    }
}
