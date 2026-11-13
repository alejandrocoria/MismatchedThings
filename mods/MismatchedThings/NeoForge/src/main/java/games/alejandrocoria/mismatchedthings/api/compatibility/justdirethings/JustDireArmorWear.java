package games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings.JustDireArmorWearRouter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** NeoForge-only incoming wear dispatch, available since Things 1.1.0. */
public final class JustDireArmorWear {
    private JustDireArmorWear() {
    }

    /**
     * Register once during product initialization, independently of native resource binding.
     * Duplicate slots reject without replacing their owner. Products remain responsible for
     * eligibility, transactional payment and exactly-once ordinary delegation.
     */
    public static void register(EquipmentSlot slot, Handler handler) {
        JustDireArmorWearRouter.register(slot, handler);
    }

    @FunctionalInterface
    public interface Handler {
        /** Called only from a verified post-event consumer, never for direct item wear. */
        void handle(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                    Operation<Void> ordinary, float originalDamage);
    }
}
