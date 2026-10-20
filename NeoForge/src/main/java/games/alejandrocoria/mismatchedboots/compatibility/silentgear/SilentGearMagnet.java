package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Optional readiness only in A; mixed item dispatch is deliberately not connected yet. */
public final class SilentGearMagnet {
    private static volatile SilentGearMagnetApi api;
    private static volatile boolean disabled;
    private SilentGearMagnet() {}

    public static void initialize(IEventBus bus) { if (ModList.get().isLoaded("silentgear")) { bus.addListener(SilentGearMagnet::bind); } }
    private static void bind(RegisterCapabilitiesEvent event) {
        if (disabled) { return; }
        try { var binding = new SilentGearMagnetApi(); binding.requireProof(); api = binding; }
        catch (Throwable failure) { disable(failure); }
    }
    public static boolean available() { return api != null && !disabled; }
    static synchronized void disable(Throwable failure) {
        api = null;
        if (disabled) { return; }
        disabled = true; Constants.LOG.warn("Silent Gear mixed magnet disabled: native magnet contract unavailable", failure);
    }
}
