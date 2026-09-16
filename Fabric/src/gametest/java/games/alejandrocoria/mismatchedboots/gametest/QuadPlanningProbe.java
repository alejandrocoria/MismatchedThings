package games.alejandrocoria.mismatchedboots.gametest;

public final class QuadPlanningProbe {
    private static boolean armed;
    private static int count;

    private QuadPlanningProbe() {
    }

    public static void arm() {
        count = 0;
        armed = true;
    }

    public static void record() {
        if (armed) {
            count++;
        }
    }

    public static int take() {
        int result = count;
        clear();
        return result;
    }

    public static void clear() {
        armed = false;
        count = 0;
    }
}
