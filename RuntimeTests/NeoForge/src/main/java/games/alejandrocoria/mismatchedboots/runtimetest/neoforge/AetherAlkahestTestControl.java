package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Test-only observers/opaque identity controls, never another protection implementation. */
public final class AetherAlkahestTestControl {
    public static int observations;
    public static int bindingFailures;

    private AetherAlkahestTestControl() {
    }

    public static void observe() {
        observations++;
    }

    public static boolean result(boolean value) {
        observe();
        return value;
    }

    public static ItemStack opaqueStack(ItemStack value) {
        return value;
    }

    public static Supplier<?> opaqueSupplier(Supplier<?> value) {
        return value;
    }

    public static boolean bindingFault(boolean armor) {
        return System.getProperty("mismatchedboots.runtimetest.aetherAlkahest", "").equals(armor ? "armorbinding" : "fluidbinding");
    }
}
