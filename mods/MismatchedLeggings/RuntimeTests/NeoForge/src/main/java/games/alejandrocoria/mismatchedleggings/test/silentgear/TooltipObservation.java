package games.alejandrocoria.mismatchedleggings.test.silentgear;

/** Server-safe counter: the native witness must never resolve client classes on dedicated startup. */
public final class TooltipObservation {
    static boolean active;
    static int recalculations;

    private TooltipObservation() {
    }

    public static void recalculation() {
        if (active) {
            recalculations++;
        }
    }
}
