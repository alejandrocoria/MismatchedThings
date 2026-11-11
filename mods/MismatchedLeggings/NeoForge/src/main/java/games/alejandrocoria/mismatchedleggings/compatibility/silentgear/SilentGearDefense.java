package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Independent optional contracts. FEET safety and native Bounce execution retain their existing owners. */
public final class SilentGearDefense {
    private static volatile SilentGearDefenseApi api;
    private static volatile boolean incomingDisabled;
    private static volatile boolean fallReady;
    private static volatile boolean fallDisabled;
    private static volatile boolean retaliationReady;
    private static volatile boolean retaliationDisabled;
    private static final Set<String> INCOMING_TRAITS = Set.of("silentgear:heat_resistant", "silentgear:bounce");
    private static final Set<String> refusedTraits = new HashSet<>();

    private SilentGearDefense() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener(SilentGearDefense::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        if (!incomingDisabled) {
            try {
                SilentGearDefenseApi.require("event.GearEvents", "mismatchedleggings$sgIncomingReady");
                SilentGearDefenseApi.require("gear.trait.effect.NegateDamageTraitEffect", "mismatchedleggings$sgNegateReady");
                api = new SilentGearDefenseApi();
            } catch (Throwable failure) {
                disableIncoming(failure);
            }
        }
        if (!fallDisabled) {
            try {
                SilentGearDefenseApi.require("event.GearEvents", "mismatchedleggings$sgBounceFallReady");
                fallReady = true;
            } catch (Throwable failure) {
                disableQuery(false, failure);
            }
        }
        if (!retaliationDisabled) {
            try {
                SilentGearDefenseApi.require("event.GearEvents", "mismatchedleggings$sgRetaliationReady");
                retaliationReady = true;
            } catch (Throwable failure) {
                disableQuery(true, failure);
            }
        }
    }

    public static boolean incomingAvailable() {
        return api != null && !incomingDisabled;
    }

    public static boolean fallAvailable() {
        return fallReady && !fallDisabled;
    }

    public static boolean retaliationAvailable() {
        return retaliationReady && !retaliationDisabled;
    }

    public static boolean armor(boolean original, LivingIncomingDamageEvent event, EquipmentSlot slot, ItemStack root, boolean proof) {
        return original || proof && incomingAvailable() && slot == EquipmentSlot.LEGS
                && MixedLeggingsStructure.validData(root).isPresent()
                && event.getEntity().getItemBySlot(slot) == root && !event.isCanceled()
                && !event.getEntity().level().isClientSide();
    }

    public static Object incoming(ItemStack root, Object current, Object function, Operation<Object> original,
                                  LivingIncomingDamageEvent event, float base, boolean proof) {
        if (!(root.getItem() instanceof MixedLeggingsItem)) {
            return original.call(root, current, function);
        }
        // Refused/malformed mixed roots must never fall through to full native activateTraits.
        var binding = api;
        if (!proof || binding == null || incomingDisabled || MixedLeggingsStructure.validData(root).isEmpty()
                || event.isCanceled() || event.getEntity().level().isClientSide()
                || event.getEntity().getItemBySlot(EquipmentSlot.LEGS) != root || !(current instanceof Float amount)) {
            return current;
        }

        try {
            if (!Float.isFinite(amount) || !Float.isFinite(base) || amount < 0 || base < 0) {
                throw new IllegalArgumentException("Unsupported incoming event amounts");
            }
            var calls = new ArrayList<Call>();
            for (var trait : binding.traits(root)) {
                String id = trait.id().toString();
                if (!INCOMING_TRAITS.contains(id) || refusedTraits.contains(id)) {
                    continue;
                }
                try {
                    Object effect = binding.admittedEffect(trait.nativeInstance());
                    binding.validate(trait.nativeInstance(), effect, amount, base);
                    calls.add(new Call(trait.nativeInstance(), effect));
                } catch (Throwable failure) {
                    refuseTrait(id, failure);
                }
            }

            // Admit the complete piece before calling a leaf. Only its complete result is published.
            var context = root.copy();
            var before = context.copy();
            float result = amount;
            for (var call : calls) {
                float prior = result;
                result = binding.incoming(call.instance, call.effect, context, event.getEntity(), event.getSource(), result, base);
                if (!Float.isFinite(result) || result < 0 || result > prior || !ItemStack.matches(before, context)) {
                    throw new IllegalStateException("Native defensive leaf violated its read-only reduction contract");
                }
            }
            return result;
        } catch (Throwable failure) {
            disableIncoming(failure);
            return current; // Never publish a partially reduced piece or replay this hit.
        }
    }

    public static int fallLevel(int original, LivingFallEvent event, boolean proof) {
        // Recognition remains inert until the native Bounce query block is enabled.
        return original;
    }

    public static int retaliationLevel(int original, LivingDamageEvent.Post event, boolean proof) {
        return original;
    }

    private static synchronized void refuseTrait(String id, Throwable failure) {
        if (refusedTraits.add(id)) {
            LeggingsContent.LOG.warn("Silent Gear mixed leggings incoming trait disabled: {}", id, failure);
        }
    }

    private record Call(Object instance, Object effect) {}

    private static synchronized void disableIncoming(Throwable failure) {
        api = null;
        if (!incomingDisabled) {
            incomingDisabled = true;
            LeggingsContent.LOG.warn("Silent Gear mixed leggings incoming disabled: native defensive contract unavailable", failure);
        }
    }

    private static synchronized void disableQuery(boolean retaliation, Throwable failure) {
        if (retaliation ? retaliationDisabled : fallDisabled) {
            return;
        }
        if (retaliation) {
            retaliationDisabled = true;
        } else {
            fallDisabled = true;
        }
        LeggingsContent.LOG.warn("Silent Gear mixed leggings {} disabled: native Bounce query contract unavailable",
                retaliation ? "retaliation" : "bounce recognition", failure);
    }
}
