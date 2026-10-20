package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Native other-slot Bounce may charge any FEET item: this barrier does not require a mixed SG trait. */
public final class SilentGearDefense {
    private static volatile boolean fallReady, fallDisabled;
    private static volatile SilentGearDefenseApi api;
    private static volatile boolean incomingDisabled;
    private static final Set<String> refusedTraits = new HashSet<>();
    private static final Set<String> INCOMING_TRAITS = Set.of("silentgear:heat_resistant", "silentgear:bounce");
    private SilentGearDefense() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) { bus.addListener(SilentGearDefense::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            SilentGearDefenseApi.require("event.GearEvents", "mismatchedboots$sgSafeFallReady");
            SilentGearDefenseApi.requireSchedule();
            fallReady = true;
        } catch (Throwable failure) { disable(failure); }
        try {
            SilentGearDefenseApi.require("event.GearEvents", "mismatchedboots$sgIncomingReady");
            SilentGearDefenseApi.require("gear.trait.effect.NegateDamageTraitEffect", "mismatchedboots$sgNegateReady");
            api = new SilentGearDefenseApi();
        } catch (Throwable failure) { disableIncoming(failure); }
    }

    public static boolean armor(boolean original, LivingIncomingDamageEvent event, EquipmentSlot slot, ItemStack root, boolean proof) {
        return original || proof && api != null && !incomingDisabled && slot == EquipmentSlot.FEET && valid(root)
                && event.getEntity().getItemBySlot(slot) == root && !event.isCanceled();
    }

    public static Object incoming(ItemStack root, Object current, Object function, Operation<Object> original,
            LivingIncomingDamageEvent event, float base, boolean proof) {
        if (!(root.getItem() instanceof MixedBootsItem)) { return original.call(root, current, function); }
        // Even refused/malformed mixed roots must never fall through to native activateTraits.
        var binding = api;
        if (!proof || binding == null || incomingDisabled || !valid(root)
                || event.isCanceled() || event.getEntity().level().isClientSide() || event.getEntity().getItemBySlot(EquipmentSlot.FEET) != root
                || !(current instanceof Float amount)) { return current; }
        try {
            var calls = new ArrayList<Call>();
            for (var trait : SilentGearCompatibility.effectiveTraits(root)) {
                String id = trait.id().toString();
                if (!INCOMING_TRAITS.contains(id) || refusedTraits.contains(id)) { continue; }
                try {
                    Object effect = binding.admittedEffect(trait.nativeInstance());
                    binding.validate(trait.nativeInstance(), effect, amount, base);
                    calls.add(new Call(trait.nativeInstance(), effect));
                } catch (Throwable failure) { refuseTrait(id, failure); }
            }
            // Native receives a disposable context, never an equipped carrier/source. Publish only the whole result.
            var context = root.copy(); var before = context.copy(); float result = amount;
            for (var call : calls) {
                result = binding.incoming(call.instance, call.effect, context, event.getEntity(), event.getSource(), result, base);
                if (!Float.isFinite(result) || result < 0 || result > amount || !ItemStack.matches(before, context)) {
                    throw new IllegalStateException("Native defensive leaf violated its read-only reduction contract");
                }
            }
            return result;
        } catch (Throwable failure) {
            disableIncoming(failure);
            return current; // No partial reduction or replay of this piece/hit.
        }
    }

    private static synchronized void refuseTrait(String id, Throwable failure) {
        if (refusedTraits.add(id)) { Constants.LOG.warn("Silent Gear mixed incoming trait disabled: {}", id, failure); }
    }

    private static synchronized void disableIncoming(Throwable failure) {
        api = null;
        if (!incomingDisabled) {
            incomingDisabled = true;
            Constants.LOG.warn("Silent Gear mixed incoming disabled: native defensive contract unavailable", failure);
        }
    }

    private record Call(Object instance, Object effect) {}

    public static boolean valid(ItemStack root) {
        var equipment = root.get(DataComponents.EQUIPPABLE);
        return root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && ModContent.getMixedBootsData(root).isPresent() && equipment != null && equipment.slot() == EquipmentSlot.FEET;
    }

    /** A adds no mixed Bounce level. Refused wear must also deny a native peer before any branch effects. */
    public static boolean allowFall(LivingFallEvent event, boolean proof) {
        ItemStack feet = event.getEntity().getItemBySlot(EquipmentSlot.FEET);
        if (!(feet.getItem() instanceof MixedBootsItem)) { return true; }
        return proof && fallReady && !fallDisabled && valid(feet) && !event.isCanceled();
    }

    /** Only non-mixed targets may reach native gear wear, including when mixed admission is refused. */
    public static boolean allowNativeFallWear(ItemStack root, int amount, LivingEntity actor, EquipmentSlot slot, boolean proof) {
        if (!(root.getItem() instanceof MixedBootsItem)) { return true; }
        if (!proof || !fallReady || fallDisabled || !valid(root) || slot != EquipmentSlot.FEET
                || actor.level().isClientSide() || actor.getItemBySlot(slot) != root || amount <= 0) { return false; }
        // Keep normal SG durability/JDT/vanilla wear routing. No catch/retry can safely replay this payment.
        root.hurtAndBreak(amount, actor, slot);
        return false;
    }

    private static synchronized void disable(Throwable failure) {
        if (!fallDisabled) {
            fallDisabled = true;
            Constants.LOG.warn("Silent Gear mixed safe fall disabled: native fall/wear contract unavailable", failure);
        }
    }
}
