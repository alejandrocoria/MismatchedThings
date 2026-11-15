package games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedthings.api.compatibility.mysticalagriculture.MysticalRefunds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Exact-item dispatch and invocation-local staging, not purchase authority or a receipt ledger. */
public final class MysticalRefundObserver {
    // NeoForge constructs mods concurrently. Registered item identities are stable singleton keys.
    private static final Map<Item, MysticalRefunds.Factory> OWNERS = new ConcurrentHashMap<>();

    private MysticalRefundObserver() {
    }

    public static void register(Item item, MysticalRefunds.Factory factory) {
        if (item == null || factory == null) {
            throw new IllegalArgumentException("Expected a registered item and MA receipt factory");
        }
        if (OWNERS.putIfAbsent(item, factory) != null) {
            throw new IllegalStateException("Duplicate MA receipt factory for item " + item);
        }
    }

    public static Capture capture(boolean ready, ItemStack root, int damage, LivingEntity owner) {
        var factory = ready ? OWNERS.get(root.getItem()) : null;
        var receipt = factory == null ? null : factory.capture(root, damage, owner);
        return receipt == null ? null : new Capture(receipt);
    }

    public static void arm(Capture capture) {
        if (capture != null && capture.stage == Stage.CAPTURED) {
            // Close before external code: throws and recursive calls cannot retry this authorization.
            capture.stage = Stage.CLOSED;
            capture.receipt.arm();
            capture.stage = Stage.ARMED;
        }
    }

    public static void consumed(Capture capture) {
        if (capture != null && capture.stage == Stage.ARMED) {
            capture.stage = Stage.CLOSED;
            capture.receipt.consumed();
            capture.stage = Stage.CONSUMED;
        }
    }

    public static void complete(Capture capture) {
        if (capture != null && capture.stage == Stage.CONSUMED) {
            capture.stage = Stage.CLOSED;
            capture.receipt.complete();
        }
    }

    private enum Stage {
        CAPTURED,
        ARMED,
        CONSUMED,
        CLOSED
    }

    public static final class Capture {
        private final MysticalRefunds.Receipt receipt;
        private Stage stage = Stage.CAPTURED;

        private Capture(MysticalRefunds.Receipt receipt) {
            this.receipt = receipt;
        }
    }
}
