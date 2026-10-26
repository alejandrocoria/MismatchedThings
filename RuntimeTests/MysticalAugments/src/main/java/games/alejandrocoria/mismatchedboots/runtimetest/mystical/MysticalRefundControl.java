package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Foreign callbacks and read-only timing observations belong only to this fixture. */
public final class MysticalRefundControl {
    public static ItemStack root;
    public static ServerPlayer owner;
    public static Runnable before;
    public static Runnable after;
    public static Runnable duringDelivery;
    public static boolean prevent;
    public static boolean cancelDelivery;
    public static boolean throwDelivery;
    public static int deliveries;
    public static int peerCalls;

    private MysticalRefundControl() {
    }

    public static void reset(ItemStack stack, ServerPlayer player) {
        root = stack;
        owner = player;
        before = after = duringDelivery = null;
        prevent = cancelDelivery = throwDelivery = false;
        deliveries = peerCalls = 0;
    }

    public static void before() {
        peerCalls++;
        Runnable action = before;
        before = null;
        if (action != null) {
            action.run();
        }
    }

    public static void after() {
        Runnable action = after;
        after = null;
        if (action != null) {
            action.run();
        }
    }

    public static boolean delivery(Inventory inventory) {
        if (owner == null || inventory != owner.getInventory() || root == null) {
            return false;
        }
        if (!root.isEmpty()) {
            throw new AssertionError("MA delivery ran before root consumption");
        }
        deliveries++;
        Runnable action = duringDelivery;
        duringDelivery = null;
        if (action != null) {
            action.run();
        }
        if (throwDelivery) {
            throw new IllegalStateException("MA controlled delivery failure");
        }

        return cancelDelivery;
    }

    public static boolean opaqueBroken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage();
    }
}
