package games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings.JustDireRefunds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dispatch only; purchase state, receipt validation and delivery belong to the exact item owner. */
public final class JustDireRefundRouter {
    // NeoForge constructs product mods concurrently; item registry identities are stable singleton keys.
    private static final Map<Item, JustDireRefunds.Handler> HANDLERS = new ConcurrentHashMap<>();

    private JustDireRefundRouter() {
    }

    public static void register(Item item, JustDireRefunds.Handler handler) {
        if (item == null || handler == null) {
            throw new IllegalArgumentException("Expected a registered item and refund handler");
        }
        if (HANDLERS.putIfAbsent(item, handler) != null) {
            throw new IllegalStateException("Duplicate refund handler for item " + item);
        }
    }

    public static void route(boolean ready, ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary) {
        var handler = ready ? HANDLERS.get(root.getItem()) : null;
        if (handler == null) {
            ordinary.call(root, count);
            return;
        }

        // Never retry ordinary consumption after a product callback throws: mutation may be committed.
        handler.consume(root, count, owner, ordinary);
    }
}
