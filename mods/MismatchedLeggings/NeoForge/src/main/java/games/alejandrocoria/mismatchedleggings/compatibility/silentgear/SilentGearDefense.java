package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Independent optional contracts. FEET safety and native Bounce execution retain their existing owners. */
public final class SilentGearDefense {
    private static volatile SilentGearDefenseApi api;
    private static volatile boolean incomingDisabled;
    private static volatile boolean fallReady;
    private static volatile boolean fallDisabled;
    private static volatile boolean retaliationReady;
    private static volatile boolean retaliationDisabled;

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

    // Contract scaffolding only: readiness does not enable new LEGS gameplay yet.
    public static boolean armor(boolean original, LivingIncomingDamageEvent event, EquipmentSlot slot, ItemStack root, boolean proof) {
        return original;
    }

    public static Object incoming(ItemStack root, Object current, Object function, Operation<Object> original,
                                  LivingIncomingDamageEvent event, float base, boolean proof) {
        // Even an independently admitted malformed mixed item must never reach the full native dispatcher.
        return root.getItem() instanceof MixedLeggingsItem ? current : original.call(root, current, function);
    }

    public static int fallLevel(int original, LivingFallEvent event, boolean proof) {
        return original;
    }

    public static int retaliationLevel(int original, LivingDamageEvent.Post event, boolean proof) {
        return original;
    }

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
