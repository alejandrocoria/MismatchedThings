package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Disposable tier candidate only; tag protection now comes from production. */
public final class AetherAlkahestSpikeControl {
    static int fluidReads, armorReads, fluidExtensions, armorExtensions;

    private AetherAlkahestSpikeControl() {}

    public static boolean tier(boolean original, ItemStack stack, Supplier<?> supplier, boolean armor) {
        if (armor) { armorReads++; } else { fluidReads++; }
        if (original || supplier.get() != AetherIICompatibilityTest.component("reinforcement_tier")) { return original; }
        boolean extended = MixedBootsSources.anySource(stack, source -> source.has(AetherIICompatibilityTest.component("reinforcement_tier")));
        if (extended) { if (armor) { armorExtensions++; } else { fluidExtensions++; } }
        return extended;
    }
}
