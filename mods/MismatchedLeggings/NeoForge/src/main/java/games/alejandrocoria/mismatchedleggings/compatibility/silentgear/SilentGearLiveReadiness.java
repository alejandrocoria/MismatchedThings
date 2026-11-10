package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

/** Published only after all competing transformations of the selected native class have finished. */
public interface SilentGearLiveReadiness {
    default boolean mismatchedleggings$sgLiveReady() { return false; }
}
