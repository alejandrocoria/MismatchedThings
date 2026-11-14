package games.alejandrocoria.mismatchedleggings.test.refundspike;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Proposed Things-owned boundary. No product classes, slot policies or credit schema. */
public final class SharedConsumption {
    private static final Map<Item, Handler> HANDLERS = new IdentityHashMap<>();
    public static int acceptedCalls;
    public static int refusedCalls;

    private SharedConsumption() {}

    public static void register(Item item, Handler handler) {
        if (HANDLERS.putIfAbsent(item, handler) != null) {
            throw new IllegalStateException("Duplicate exact-item refund handler");
        }
    }

    public static void route(boolean ready, ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary) {
        var handler = ready ? HANDLERS.get(root.getItem()) : null;
        if (handler == null) {
            refusedCalls++;
            ordinary.call(root, count);
            return;
        }
        acceptedCalls++;
        handler.consume(root, count, owner, ordinary);
    }

    @FunctionalInterface
    public interface Handler {
        void consume(ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary);
    }
}
