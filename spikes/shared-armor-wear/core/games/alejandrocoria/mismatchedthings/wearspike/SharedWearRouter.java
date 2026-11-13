package games.alejandrocoria.mismatchedthings.wearspike;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Disposable core dispatch only: no source schema, FE binding or product references. */
public final class SharedWearRouter {
    // NeoForge product constructors may register concurrently; publish each slot atomically.
    private static final Map<EquipmentSlot, Handler> handlers = new ConcurrentHashMap<>();

    private SharedWearRouter() {}

    public static void register(EquipmentSlot slot, Handler handler) {
        if (slot == null || handler == null || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            throw new IllegalArgumentException("Expected an armor slot and wear handler");
        }
        if (handlers.putIfAbsent(slot, handler) != null) {
            throw new IllegalStateException("Duplicate armor wear handler for " + slot);
        }
    }

    public static void route(boolean ready, ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                             Operation<Void> ordinary, float originalDamage) {
        var handler = ready ? handlers.get(slot) : null;
        if (handler == null) {
            ordinary.call(root, amount, entity, slot);
            return;
        }
        // Exactly one owner receives the Operation. It decides whether to pay or delegate once.
        // Do not catch payment failures and retry ordinary wear after a possible resource commit.
        handler.route(true, root, amount, entity, slot, ordinary, originalDamage);
    }

    @FunctionalInterface
    public interface Handler {
        void route(boolean ready, ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                   Operation<Void> ordinary, float originalDamage);
    }
}
