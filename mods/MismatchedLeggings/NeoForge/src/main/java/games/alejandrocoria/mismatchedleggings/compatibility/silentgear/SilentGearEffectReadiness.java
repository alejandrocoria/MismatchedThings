package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

/** Final transformed local effect contracts, independent of optional native types. */
public interface SilentGearEffectReadiness {
    default boolean mismatchedleggings$sgRepairReady() {
        return false;
    }

    default boolean mismatchedleggings$sgWearReady() {
        return false;
    }
}
