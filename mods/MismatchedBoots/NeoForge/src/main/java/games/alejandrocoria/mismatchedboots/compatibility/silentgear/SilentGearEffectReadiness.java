package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

/** Final transformed local effect contracts, independent of optional native types. */
public interface SilentGearEffectReadiness {
    default boolean mismatchedboots$sgRepairReady() {
        return false;
    }

    default boolean mismatchedboots$sgWearReady() {
        return false;
    }
}
