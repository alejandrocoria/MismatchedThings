package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

public final class BeltExtraPredicateFixture {
    public static int calls;
    public static boolean result;

    private BeltExtraPredicateFixture() {
    }

    public static void observe(boolean value) {
        calls++;
        result = value;
    }
}
