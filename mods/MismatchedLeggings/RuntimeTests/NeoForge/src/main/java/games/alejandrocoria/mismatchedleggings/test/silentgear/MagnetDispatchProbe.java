package games.alejandrocoria.mismatchedleggings.test.silentgear;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Observes only the mod-owned native invocation boundary; never changes native code or grants readiness. */
public final class MagnetDispatchProbe {
    static boolean observing;
    static boolean failAfterCall;
    static final List<Object> instances = new ArrayList<>();
    private static ItemStack carrier;
    private static ItemStack before;

    private MagnetDispatchProbe() {}

    public static void before(Object instance, ItemStack stack) {
        if (!observing) { return; }
        instances.add(instance);
        carrier = stack;
        before = stack.copy();
    }

    public static void after() {
        if (!observing) { return; }
        require(ItemStack.matches(before, carrier), "Native magnet mutated/recalculated its disposable carrier");
        if (failAfterCall) { throw new IllegalStateException("Controlled failure after first native magnet request"); }
    }

    public static void recalculation() {
        require(!observing, "Read-only magnet dispatch entered native gear recalculation");
    }
}
