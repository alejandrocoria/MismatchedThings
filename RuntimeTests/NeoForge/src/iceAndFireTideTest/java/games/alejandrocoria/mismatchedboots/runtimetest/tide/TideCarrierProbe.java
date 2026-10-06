package games.alejandrocoria.mismatchedboots.runtimetest.tide;

import net.minecraft.world.item.ItemStack;

/** Deliberately stack-sensitive fixture hook, without changing native effects. */
public final class TideCarrierProbe {
    static int reads;
    static int baseReads;
    static int baseMutations;
    static int extraCalls;

    private TideCarrierProbe() {}

    public static void readCarrier(ItemStack stack) {
        if (stack.getCount() < 1) { throw new AssertionError("Unexpected empty tick carrier"); }
        reads++;
    }

    public static void baseTick(ItemStack stack) {
        if (!stack.getItem().getClass().getName().equals("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem")) { return; }
        baseReads++;
        if (System.getProperty("mismatchedboots.runtimetest.tideOwners", "").equals("baseMutate")) {
            stack.set(net.minecraft.core.component.DataComponents.REPAIR_COST, stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0) + 1);
            baseMutations++;
        }
    }

    public static void extraWork() { extraCalls++; }

    public static boolean chain(boolean original) { extraCalls++; return original; }
}
