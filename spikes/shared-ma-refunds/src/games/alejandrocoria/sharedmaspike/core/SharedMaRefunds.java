package games.alejandrocoria.sharedmaspike.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Candidate core API: no product/native-MA classes, purchase state or global receipts. */
public final class SharedMaRefunds {
    private static final Map<Item, Factory> OWNERS = new ConcurrentHashMap<>();

    private SharedMaRefunds() {
    }

    public static void register(Item item, Factory factory) {
        if (item == null || factory == null || OWNERS.putIfAbsent(item, factory) != null) {
            throw new IllegalArgumentException("Duplicate or missing MA owner");
        }
    }

    public static Capture capture(boolean ready, ItemStack root, int damage, LivingEntity owner) {
        Factory factory = ready ? OWNERS.get(root.getItem()) : null;
        Receipt receipt = factory == null ? null : factory.capture(root, damage, owner);
        return receipt == null ? null : new Capture(receipt);
    }

    public static void arm(Capture capture) {
        if (capture != null && capture.stage == 0) {
            capture.stage = -1;
            capture.receipt.arm();
            capture.stage = 1;
        }
    }

    public static void consumed(Capture capture) {
        if (capture != null && capture.stage == 1) {
            capture.stage = -1;
            capture.receipt.consumed();
            capture.stage = 2;
        }
    }

    public static void complete(Capture capture) {
        if (capture != null && capture.stage == 2) {
            capture.stage = -1;
            capture.receipt.complete();
        }
    }

    @FunctionalInterface
    public interface Factory {
        Receipt capture(ItemStack root, int damage, LivingEntity owner);
    }

    public interface Receipt {
        void arm();
        void consumed();
        void complete();
    }

    public static final class Capture {
        private final Receipt receipt;
        private int stage;

        private Capture(Receipt receipt) {
            this.receipt = receipt;
        }
    }
}
