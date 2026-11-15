package games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings.JustDireRefundRouter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** NeoForge actual-consumption dispatch, available since Things 1.1.0. No shared purchase state. */
public final class JustDireRefunds {
    private JustDireRefunds() {
    }

    /**
     * Register once after the product's item registry is available, independently of native binding.
     * Duplicate item identities reject without replacing their owner. No sibling is required.
     */
    public static void register(Item item, Handler handler) {
        JustDireRefundRouter.register(item, handler);
    }

    @FunctionalInterface
    public interface Handler {
        /**
         * Called only at a verified exhausted-stack consumption site. The product captures its
         * local entitlement, delegates once, and validates the actual receipt before delivery.
         * Supported locations, reentry, snapshots and cancelled/throwing drops remain local.
         */
        void consume(ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary);
    }
}
