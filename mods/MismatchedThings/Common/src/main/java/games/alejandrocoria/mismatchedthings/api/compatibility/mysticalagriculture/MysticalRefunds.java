package games.alejandrocoria.mismatchedthings.api.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture.MysticalRefundObserver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Optional MA break observation, since Things 1.1.0. Products own purchases, validation and delivery. */
public final class MysticalRefunds {
    private MysticalRefunds() {
    }

    /**
     * Register once during normal initialization, after the item identity is stable.
     * Safe for concurrent product construction; duplicates reject without replacing the owner.
     * Registration does not require MA bindings to be available and does not enable the hook.
     */
    public static void register(Item item, Factory factory) {
        MysticalRefundObserver.register(item, factory);
    }

    @FunctionalInterface
    public interface Factory {
        /**
         * Capture product-local authority or return null to decline. Damage is the proposed
         * damage value, not a delta. Keep the original root/owner and any required live component
         * view: empty-stack getters no longer expose the consumed root's authority.
         */
        Receipt capture(ItemStack root, int damage, LivingEntity owner);
    }

    /**
     * Invocation-local receipt; never persistent data. Validate product authority at each stage.
     * Exceptions propagate without retry or rollback. A throwing native callback skips completion.
     * Reentry/ownership checks across separate captures remain the product's responsibility.
     */
    public interface Receipt {
        /** Last eligibility check, after pre-break hooks and immediately before proved consumption. */
        void arm();

        /** Observe the actual consumption before the original native callback. */
        void consumed();

        /** Normal applyDamage return only. Validate the final receipt, then deliver at most once. */
        void complete();
    }
}
