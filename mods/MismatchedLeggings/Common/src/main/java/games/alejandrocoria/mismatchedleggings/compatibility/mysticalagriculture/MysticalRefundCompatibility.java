package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.api.compatibility.mysticalagriculture.MysticalRefunds;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Product-local purchase authority and delivery through Things' normal-return MA observer. */
public final class MysticalRefundCompatibility {
    private static boolean warned;

    private MysticalRefundCompatibility() {
    }

    /** Called once after the loader has registered this product's item, without resolving native MA bindings. */
    public static void register(Item item) {
        MysticalRefunds.register(item, MysticalRefundCompatibility::capture);
    }

    static synchronized void unavailable(Throwable failure) {
        if (!warned) {
            warned = true;
            LeggingsContent.LOG.warn("Mystical Agriculture augment refund binding unavailable", failure);
        }
    }

    private static Receipt capture(ItemStack root, int damage, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || root.getCount() != 1 || !root.isDamageableItem() || root.getMaxDamage() <= 0
                || damage < root.getMaxDamage()) {
            return null;
        }

        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (inventory.getItem(index) == root) {
                if (slot >= 0) {
                    return null; // Ambiguous physical ownership is not a purchase.
                }
                slot = index;
            }
        }
        if (slot < 0) {
            return null;
        }

        var purchases = MysticalAugmentsCompatibility.refunds(root);
        return purchases == null ? null : new Receipt(root, root.getItem(), player, inventory, slot,
                root.getMaxDamage(), root.getComponents(), root.get(LeggingsContent.mixedLeggingsDataType()), purchases);
    }

    private static final class Receipt implements MysticalRefunds.Receipt {
        private final ItemStack root;
        private final Item item;
        private final ServerPlayer player;
        private final Inventory inventory;
        private final int slot;
        private final int maximum;
        private final DataComponentMap live;
        private final MixedLeggingsData data;
        private final MysticalAugmentsCompatibility.RefundView purchases;
        private boolean armed;
        private boolean consumed;

        private Receipt(ItemStack root, Item item, ServerPlayer player, Inventory inventory, int slot,
                        int maximum, DataComponentMap live, MixedLeggingsData data, MysticalAugmentsCompatibility.RefundView purchases) {
            this.root = root;
            this.item = item;
            this.player = player;
            this.inventory = inventory;
            this.slot = slot;
            this.maximum = maximum;
            this.live = live;
            this.data = data;
            this.purchases = purchases;
        }

        @Override
        public void arm() {
            armed = eligible() && root.getCount() == 1 && root.getItem() == item
                    && root.isDamageableItem() && root.getDamageValue() == maximum
                    && root.getMaxDamage() == maximum && unchanged();
        }

        @Override
        public void consumed() {
            consumed = armed && eligible() && root.getCount() == 0 && unchanged();
        }

        @Override
        public void complete() {
            boolean authorized = consumed;
            consumed = false;
            armed = false;
            if (!authorized || !eligible() || root.getCount() != 0 || !unchanged()) {
                return;
            }

            // Authorization is already closed before external insertion/drop callbacks. Never retry delivery.
            for (Item purchase : purchases.items()) {
                inventory.placeItemBackInInventory(new ItemStack(purchase));
            }
        }

        private boolean eligible() {
            return player.getInventory() == inventory && inventory.getItem(slot) == root
                    && !player.hasInfiniteMaterials() && !player.isSpectator();
        }

        private boolean unchanged() {
            // Empty-stack getters hide components; retain the live map, not a stale pre-break copy.
            var shadow = new ItemStack(item, 1);
            List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
            shadow.applyComponents(live);
            return shadow.getDamageValue() == maximum && shadow.getMaxDamage() == maximum
                    && data.equals(shadow.get(LeggingsContent.mixedLeggingsDataType()))
                    && purchases.equals(MysticalAugmentsCompatibility.refunds(shadow));
        }
    }
}
