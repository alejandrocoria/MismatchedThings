package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Adversarial callbacks and timing witnesses exist only in the maintained fixture. */
public final class RefundControl {
    public static final java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
    public static ItemStack root;
    public static Inventory inventory;
    public static Runnable before, delivery;
    public static boolean cancel, cancelDelivery, throwDelivery;
    public static int deliveries;
    private RefundControl() {}
    public static void reset(ItemStack stack, Inventory owner) {
        root = stack; inventory = owner; before = delivery = null;
        cancel = cancelDelivery = throwDelivery = false; deliveries = 0;
    }
    public static boolean opaqueBroken(ItemStack stack) { return stack.isBroken(); }
    public static void before(ItemStack stack) {
        if (root != stack) { return; }
        Runnable action = before; before = null;
        if (action != null) { action.run(); }
    }
    public static boolean delivery(Inventory owner) {
        if (owner != inventory || root == null) { return false; }
        if (!root.isEmpty()) { throw new AssertionError("Refund before actual consumption"); }
        deliveries++;
        Runnable action = delivery; delivery = null;
        if (action != null) { action.run(); }
        if (throwDelivery) { throw new IllegalStateException("controlled delivery"); }
        return cancelDelivery;
    }
}
