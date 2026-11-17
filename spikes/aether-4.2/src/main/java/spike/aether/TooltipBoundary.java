package spike.aether;

import net.minecraft.world.item.ItemStack;

/** Independently switchable domains let the spike prove the relocated producers actually execute. */
public final class TooltipBoundary {
    public static boolean attributes = true;
    public static boolean tiers = true;
    public static ItemStack source;
    public static int attributeCalls;
    public static int tierCalls;
    public static int suppressedAttributes;
    public static int suppressedTiers;

    private TooltipBoundary() {}
}
