package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import java.util.ArrayList;
import java.util.List;

/** Read-only witness of production rendering, never a marker producer, factory or renderer. */
public final class SeparatorObservations {
    public static final List<Integer> WIDTHS = new ArrayList<>();

    private SeparatorObservations() {}

    public static boolean enabled() { return Boolean.getBoolean("mismatchedboots.test.separatorSpike"); }
}
