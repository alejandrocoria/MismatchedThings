package games.alejandrocoria.rulesspike;

import java.util.ArrayList;
import java.util.List;

/** Client-thread publication only. Server state remains owned by production server lifecycles. */
public final class PreviewState {
    private static Snapshot snapshot;
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    public static int received;

    private PreviewState() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    public static void install(Snapshot value) {
        snapshot = java.util.Objects.requireNonNull(value);
        received++;
        List.copyOf(LISTENERS).forEach(Runnable::run);
    }

    public static void reset() {
        snapshot = null;
        List.copyOf(LISTENERS).forEach(Runnable::run);
    }

    public static void listen(Runnable listener) {
        LISTENERS.add(listener);
        if (snapshot != null) {
            listener.run();
        }
    }
}
