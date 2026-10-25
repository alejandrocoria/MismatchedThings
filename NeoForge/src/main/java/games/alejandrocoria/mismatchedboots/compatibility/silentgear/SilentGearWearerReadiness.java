package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

/** Both local set predicates share one final-export permission; default denial is intentional. */
public interface SilentGearWearerReadiness {
    default boolean mismatchedboots$sgSetReady() {
        return false;
    }
}
