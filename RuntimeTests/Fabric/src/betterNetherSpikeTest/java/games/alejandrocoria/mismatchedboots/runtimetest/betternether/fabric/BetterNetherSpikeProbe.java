package games.alejandrocoria.mismatchedboots.runtimetest.betternether.fabric;

public final class BetterNetherSpikeProbe {
    private static boolean apiCompatible;
    private static String selectedPriority = "";
    private static int highEvaluations;
    private static int lowEvaluations;
    private static int claims;
    private static int boundaryFailures;

    private BetterNetherSpikeProbe() {
    }

    public static void recordPluginDecision(boolean compatible, String priority) {
        apiCompatible = compatible;
        selectedPriority = priority;
    }

    public static boolean apiCompatible() {
        return apiCompatible;
    }

    public static String selectedPriority() {
        return selectedPriority;
    }

    public static void resetCounters() {
        highEvaluations = 0;
        lowEvaluations = 0;
        claims = 0;
        boundaryFailures = 0;
    }

    public static void recordEvaluation(String priority) {
        if ("high".equals(priority)) {
            highEvaluations++;
        } else if ("low".equals(priority)) {
            lowEvaluations++;
        }
    }

    public static void recordClaim() {
        claims++;
    }

    public static void recordBoundaryFailure() {
        boundaryFailures++;
    }

    public static int highEvaluations() {
        return highEvaluations;
    }

    public static int lowEvaluations() {
        return lowEvaluations;
    }

    public static int claims() {
        return claims;
    }

    public static int boundaryFailures() {
        return boundaryFailures;
    }
}
