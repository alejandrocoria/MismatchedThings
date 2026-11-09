package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Observations only: the native/Boots consumption operation is never replaced or replayed. */
public final class MysticalRefundCompatibility {
    private static boolean warned;

    private MysticalRefundCompatibility() {}

    static synchronized void unavailable(Throwable failure) {
        if (warned) { return; }
        warned = true;
        if (failure == null) {
            LeggingsContent.LOG.warn("Mystical Agriculture leggings refund flow incompatible; ordinary breakage retained without augment refunds");
        } else {
            LeggingsContent.LOG.warn("Mystical Agriculture leggings refund binding unavailable", failure);
        }
    }

    public static Capture capture(boolean ready, ItemStack root, int damage, LivingEntity owner) {
        if (!ready || !(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || root.getCount() != 1 || !root.isDamageableItem() || root.getMaxDamage() <= 0
                || damage < root.getMaxDamage()) {
            return null;
        }
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == root) {
                if (slot >= 0) { return null; } // Ambiguous physical ownership is not a purchase.
                slot = i;
            }
        }
        if (slot < 0) { return null; }
        var purchases = MysticalAugmentsCompatibility.refunds(root);
        return purchases == null ? null : new Capture(root, root.getItem(), player, inventory, slot,
                root.getMaxDamage(), root.getComponents(), root.get(LeggingsContent.mixedLeggingsDataType()), purchases);
    }

    /** Runs after native pre-break events, immediately before the proved one-item consumption. */
    public static void arm(Capture capture) {
        if (capture == null || capture.stage != 0) { return; }
        capture.stage = -1;
        if (eligible(capture) && capture.root.getCount() == 1 && capture.root.getItem() == capture.item
                && capture.root.isDamageableItem() && capture.root.getDamageValue() == capture.maximum
                && capture.root.getMaxDamage() == capture.maximum && unchanged(capture)) {
            capture.stage = 1;
        }
    }

    public static void consumed(Capture capture) {
        if (capture == null || capture.stage != 1) { return; }
        capture.stage = eligible(capture) && capture.root.getCount() == 0 && unchanged(capture) ? 2 : -1;
    }

    /** Called only on normal applyDamage return; consume authorization before inventory/drop callbacks. */
    public static void complete(Capture capture) {
        if (capture == null || capture.stage != 2) { return; }
        capture.stage = -1;
        if (!eligible(capture) || capture.root.getCount() != 0 || !unchanged(capture)) { return; }
        for (Item item : capture.purchases.items()) {
            capture.inventory.placeItemBackInInventory(new ItemStack(item));
        }
    }

    private static boolean eligible(Capture capture) {
        return capture.player.getInventory() == capture.inventory && capture.inventory.getItem(capture.slot) == capture.root
                && !capture.player.hasInfiniteMaterials() && !capture.player.isSpectator();
    }

    private static boolean unchanged(Capture capture) {
        // Empty-stack getters hide components; retain the live map, not a stale pre-break copy.
        var shadow = new ItemStack(capture.item, 1);
        List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
        shadow.applyComponents(capture.live);
        return shadow.getDamageValue() == capture.maximum && shadow.getMaxDamage() == capture.maximum
                && capture.data.equals(shadow.get(LeggingsContent.mixedLeggingsDataType()))
                && capture.purchases.equals(MysticalAugmentsCompatibility.refunds(shadow));
    }

    /** Invocation-local state held by @Share; no thread-local or persistent credit survives a throw. */
    public static final class Capture {
        private final ItemStack root;
        private final Item item;
        private final ServerPlayer player;
        private final Inventory inventory;
        private final int slot;
        private final int maximum;
        private final DataComponentMap live;
        private final MixedLeggingsData data;
        private final MysticalAugmentsCompatibility.RefundView purchases;
        private int stage;

        private Capture(ItemStack root, Item item, ServerPlayer player, Inventory inventory, int slot,
                        int maximum, DataComponentMap live, MixedLeggingsData data, MysticalAugmentsCompatibility.RefundView purchases) {
            this.root = root; this.item = item; this.player = player; this.inventory = inventory;
            this.slot = slot; this.maximum = maximum; this.live = live; this.data = data; this.purchases = purchases;
        }
    }
}
