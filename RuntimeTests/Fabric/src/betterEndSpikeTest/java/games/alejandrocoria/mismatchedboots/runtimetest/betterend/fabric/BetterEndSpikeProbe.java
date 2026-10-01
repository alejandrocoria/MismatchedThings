package games.alejandrocoria.mismatchedboots.runtimetest.betterend.fabric;

public final class BetterEndSpikeProbe {
    private static int delegatedCalls;

    private BetterEndSpikeProbe() {
    }

    public static void reset() {
        delegatedCalls = 0;
    }

    public static void recordDelegation() {
        delegatedCalls++;
    }

    public static int delegatedCalls() {
        return delegatedCalls;
    }
}
