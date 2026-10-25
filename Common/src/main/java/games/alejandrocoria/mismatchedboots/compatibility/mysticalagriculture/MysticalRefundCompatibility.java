package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Per-call physical entitlement, not an effect list or a persistent resource account. */
public final class MysticalRefundCompatibility {
    private static final ThreadLocal<Set<ItemStack>> IN_FLIGHT = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static boolean warned;

    private MysticalRefundCompatibility() {
    }

    static synchronized void unavailable(Throwable failure) {
        if (warned) {
            return;
        }
        warned = true;
        if (failure == null) {
            Constants.LOG.warn("Mystical Agriculture refund flow incompatible; ordinary breakage retained without augment refunds");
        } else {
            Constants.LOG.warn("Mystical Agriculture augment refund binding unavailable", failure);
        }
    }

    public static void route(boolean ready, ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary) {
        Capture capture = ready ? capture(root, count, owner) : null;
        if (capture == null) {
            ordinary.call(root, count);
            return;
        }
        Set<ItemStack> active = IN_FLIGHT.get();
        if (!active.add(root)) {
            ordinary.call(root, count);
            return;
        }
        try {
            ordinary.call(root, count);
            complete(capture); // No delivery before the actual one-item break consumption returns.
        } finally {
            active.remove(root);
            if (active.isEmpty()) {
                IN_FLIGHT.remove();
            }
        }
    }

    private static Capture capture(ItemStack root, int count, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || count != 1 || root.getCount() != 1 || !root.isDamageableItem()
                || root.getMaxDamage() <= 0 || root.getDamageValue() < root.getMaxDamage()) {
            return null;
        }
        int slot = -1;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == root) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return null;
        }
        var purchases = MysticalAugmentsCompatibility.refunds(root);
        return purchases == null ? null : new Capture(root, root.getItem(), player, inventory, slot, root.getComponents(), purchases);
    }

    private static void complete(Capture capture) {
        if (capture.root.getCount() != 0 || capture.player.getInventory() != capture.inventory
                || capture.inventory.getItem(capture.slot) != capture.root
                || capture.player.hasInfiniteMaterials() || capture.player.isSpectator()) {
            return;
        }
        // Empty-stack getters hide components. The live pre-shrink map exposes entitlement changes,
        // without treating unrelated root lore or components as an entire-stack fingerprint.
        var shadow = new ItemStack(capture.item, 1);
        List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
        shadow.applyComponents(capture.live);
        if (!capture.purchases.equals(MysticalAugmentsCompatibility.refunds(shadow))) {
            return;
        }
        // The one local authorization is consumed here, before external inventory/drop callbacks.
        // An exception/cancelled drop never resurrects the root or retries a dispatched purchase.
        for (Item item : capture.purchases.items()) {
            capture.player.getInventory().placeItemBackInInventory(new ItemStack(item));
        }
    }

    private record Capture(ItemStack root, Item item, ServerPlayer player, Inventory inventory, int slot, DataComponentMap live,
                           MysticalAugmentsCompatibility.RefundView purchases) {
    }
}
