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

/** Audited potion effects only. Repair, wear and native gear ticking have separate owners. */
public final class SilentGearWearer {
    private static volatile SilentGearWearerApi api;
    private static volatile boolean disabled;

    private SilentGearWearer() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearWearer::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var binding = new SilentGearWearerApi();
            binding.requireProof();
            api = binding;
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    public static boolean predicate(boolean original, Object context, EquipmentSlot slot, ItemStack stack) {
        if (original || disabled || api == null || slot != EquipmentSlot.FEET) {
            return original;
        }
        try {
            return api.recognizes(stack, context);
        } catch (Throwable failure) {
            disable(failure);
            return original;
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var binding = api;
        if (binding == null || disabled || slot == null || !(entity instanceof Player player) || player.tickCount % 10 != 0) {
            return;
        }

        try {
            var calls = new ArrayList<Call>();
            // Validate the complete batch before publishing any native potion. Stable max-by-ID view
            // avoids duplicate effects from two sources; Stellar's SelfRepair is deliberately not called.
            for (var trait : SilentGearCompatibility.effectiveTraits(root)) {
                if (!SilentGearWearerApi.WIELDER_IDS.contains(trait.id().toString())) {
                    continue;
                }
                for (var effect : binding.admittedEffects(trait.nativeInstance())) {
                    calls.add(new Call(trait, effect));
                }
            }
            if (calls.isEmpty()) {
                return;
            }

            ItemStack carrier = binding.carrier(root);
            ItemStack before = carrier.copy();
            for (var call : calls) {
                binding.tick(call.effect, player, call.trait.nativeInstance(), carrier, true);
                if (!ItemStack.matches(before, carrier)) {
                    throw new IllegalStateException("Wearer callback changed its carrier");
                }
            }
        } catch (Throwable failure) {
            // A callback may already have affected the actor: never retry, erase potions or claim rollback.
            disable(failure);
        }
    }

    private static synchronized void disable(Throwable failure) {
        if (disabled) {
            return;
        }
        disabled = true;
        Constants.LOG.warn("Silent Gear mixed wielder disabled: wearer contract unavailable", failure);
    }

    private record Call(SilentGearCompatibility.Trait trait, Object effect) {
    }
}
