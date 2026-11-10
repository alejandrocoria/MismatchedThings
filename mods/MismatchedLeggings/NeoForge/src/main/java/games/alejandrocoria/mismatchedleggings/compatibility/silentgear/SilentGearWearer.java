package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;

/** Paired LEGS recognition and native potions share one fail-closed lifetime, independent of repair/Brilliant. */
public final class SilentGearWearer {
    private static volatile SilentGearWearerApi api;
    private static volatile boolean disabled;

    private SilentGearWearer() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener((RegisterCapabilitiesEvent event) -> {
                try {
                    var binding = new SilentGearWearerApi();
                    binding.requireProof();
                    api = binding;
                } catch (Throwable failure) {
                    disable(failure);
                }
            });
        }
    }

    public static boolean recognizes(Object context, EquipmentSlot slot, ItemStack root) {
        var binding = api;
        if (binding == null || disabled || slot != EquipmentSlot.LEGS) { return false; }
        try {
            return binding.recognizes(root, context);
        } catch (Throwable failure) {
            disable(failure);
            return false;
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var binding = api;
        if (binding == null || disabled || slot == null || !(entity instanceof Player player) || player.tickCount % 10 != 0) { return; }
        try {
            var calls = new ArrayList<Call>();
            // Resolve/revalidate the whole intended batch before any actor callback. No companion dispatch.
            var traits = binding.traits(root);
            for (var trait : traits) {
                if (!SilentGearWearerApi.WIELDER_IDS.contains(trait.id().toString())) { continue; }
                for (var effect : binding.admittedEffects(trait.nativeInstance())) {
                    calls.add(new Call(trait.nativeInstance(), effect));
                }
            }
            if (calls.isEmpty()) { return; }
            var carrier = binding.carrier(root);
            var contexts = new ArrayList<Object>();
            for (var call : calls) { contexts.add(binding.context(player, call.trait, carrier)); }
            var before = carrier.copy();
            for (int i = 0; i < calls.size() && !disabled; i++) {
                binding.tick(calls.get(i).effect, contexts.get(i), player, slot != null);
                if (!ItemStack.matches(before, carrier)) {
                    throw new IllegalStateException("Native Wielder callback changed its carrier");
                }
            }
        } catch (Throwable failure) {
            // Actor changes may already have happened. Never replay the batch or erase existing potions.
            disable(failure);
        }
    }

    private static synchronized void disable(Throwable failure) {
        if (disabled) { return; }
        disabled = true;
        LeggingsContent.LOG.warn("Silent Gear mixed leggings wielder disabled: wearer/set contract unavailable", failure);
    }

    private record Call(Object trait, Object effect) {}
}
