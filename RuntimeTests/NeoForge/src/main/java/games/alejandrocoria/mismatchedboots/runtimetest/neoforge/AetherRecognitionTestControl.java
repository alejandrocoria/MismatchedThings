package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.core.Holder;

import java.util.function.Supplier;

/** Observation/opaque-operand controls only; never recognize mixed sources or grant abilities. */
public final class AetherRecognitionTestControl {
    static int setReads, sentryReads;

    private AetherRecognitionTestControl() {
    }

    public static Object setResult(Object original) {
        setReads++;
        return original;
    }

    public static boolean sentryResult(boolean original) {
        sentryReads++;
        return original;
    }

    public static Supplier<?> opaqueSupplier(Supplier<?> original) {
        return original;
    }

    public static Holder<?> opaqueHolder(Holder<?> original) {
        return original;
    }
}
