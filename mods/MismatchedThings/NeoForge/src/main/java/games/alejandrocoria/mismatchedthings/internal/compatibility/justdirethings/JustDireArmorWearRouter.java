package games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings.JustDireArmorWear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Dispatch only; source eligibility, resource bindings and payment belong to each product. */
public final class JustDireArmorWearRouter {
    // NeoForge constructs product mods concurrently. Publish each slot atomically.
    private static final Map<EquipmentSlot, JustDireArmorWear.Handler> handlers = new ConcurrentHashMap<>();

    private JustDireArmorWearRouter() {
    }

    public static void register(EquipmentSlot slot, JustDireArmorWear.Handler handler) {
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

        // The single owner either pays or delegates. Retrying ordinary wear after an exception
        // could damage an item whose resource payment already committed.
        handler.handle(root, amount, entity, slot, ordinary, originalDamage);
    }
}
