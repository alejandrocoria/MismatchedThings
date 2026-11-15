package games.alejandrocoria.mismatchedleggings.test.refunds;

import net.minecraft.world.item.ItemStack;

/** Foreign mutations inside real shrink, not a replacement for the production receipt or dispatch. */
public final class RefundMutations {
    public static ItemStack root;
    public static Runnable before;
    public static Runnable after;
    public static boolean skip;

    private RefundMutations() {
    }

    public static boolean before(ItemStack stack) {
        if (stack != root) {
            return false;
        }
        var action = before;
        before = null;
        if (action != null) {
            action.run();
        }
        return skip;
    }

    public static void after(ItemStack stack) {
        if (stack == root) {
            var action = after;
            after = null;
            if (action != null) {
                action.run();
            }
        }
    }

    public static void reset() {
        root = null;
        before = null;
        after = null;
        skip = false;
    }
}
