package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;

/** Selected mixed-item magnets only; native gear ticking and the shared queue retain their owners. */
public final class SilentGearMagnet {
    private static volatile SilentGearMagnetApi api;
    private static volatile boolean disabled;

    private SilentGearMagnet() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearMagnet::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        if (disabled) {
            return;
        }
        try {
            var binding = new SilentGearMagnetApi();
            binding.requireProof();
            api = binding;
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    public static boolean available() {
        return api != null && !disabled;
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var binding = api;
        if (binding == null || disabled || level.isClientSide() || !(entity instanceof Player player)
                || player.level() != level || slot != EquipmentSlot.FEET && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return;
        }

        try {
            var calls = new ArrayList<Call>();
            // Admit the whole max-by-ID batch before any effect can enqueue work. No Wielder cadence.
            for (var trait : SilentGearCompatibility.effectiveTraits(root)) {
                if (SilentGearMagnetApi.IDS.contains(trait.id().toString())) {
                    calls.add(new Call(trait.nativeInstance(), binding.admittedEffect(trait.nativeInstance())));
                }
            }
            if (calls.isEmpty()) {
                return;
            }
            binding.requireProof();
            ItemStack carrier = binding.carrier(root);
            ItemStack before = carrier.copy();
            for (var call : calls) {
                binding.tick(call.effect, player, call.instance, carrier, true);
                if (!ItemStack.matches(before, carrier)) {
                    throw new IllegalStateException("Magnet callback changed its carrier");
                }
            }
        } catch (Throwable failure) {
            // Earlier native requests may still publish: never retry or clear another item's shared queue.
            disable(failure);
        }
    }

    static synchronized void disable(Throwable failure) {
        api = null;
        if (disabled) {
            return;
        }
        disabled = true;
        Constants.LOG.warn("Silent Gear mixed magnet disabled: native magnet contract unavailable", failure);
    }

    private record Call(Object instance, Object effect) {
    }
}
