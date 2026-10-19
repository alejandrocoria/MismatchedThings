package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/** Native effect math on disposable carriers; one validated root commit, never native gear wear. */
public final class SilentGearEffects {
    private static Api api;
    private static boolean repairDisabled, wearDisabled;
    private static final ThreadLocal<Repair> REPAIR = new ThreadLocal<>();

    private SilentGearEffects() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("silentgear")) { bus.addListener(SilentGearEffects::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try { api = new Api(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disable(true, "optional effect API unavailable", failure);
            disable(false, "optional effect API unavailable", null);
        }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        Api binding = api;
        if (binding == null || repairDisabled || !(entity instanceof Player player) || !eligible(root)
                || !root.isDamaged() || player.hasInfiniteMaterials() || root.has(DataComponents.UNBREAKABLE)) { return; }
        try {
            var calls = binding.calls(root, true);
            if (calls.isEmpty()) { return; }
            ItemStack carrier = SilentGearCompatibility.carrier(root), before = carrier.copy();
            if (carrier.isEmpty() || REPAIR.get() != null) { throw new IllegalStateException("Missing/reentrant repair carrier"); }
            Repair capture = new Repair(carrier, player);
            REPAIR.set(capture);
            try {
                for (Call call : calls) {
                    capture.calls = 0;
                    binding.repair.tick.invoke(call.effect, binding.context.invoke(player, call.trait.nativeInstance(), carrier), level, entity, slot != null);
                    if (!ItemStack.matches(before, carrier) || capture.calls > 1) { throw new IllegalStateException("Native repair escaped its local boundary"); }
                }
            } finally { REPAIR.remove(); }
            // No root write occurred during native execution; failure cannot publish a partial multi-trait heal.
            int repaired = (int) Math.min(root.getDamageValue(), capture.amount);
            if (repaired > 0) { root.setDamageValue(root.getDamageValue() - repaired); }
        } catch (Throwable failure) { disable(true, "native repair contract unavailable", failure); }
    }

    /** Only our exact carrier is intercepted. Native boots and unrelated effect invocations proceed normally. */
    public static boolean captureRepair(ItemStack stack, int amount, LivingEntity entity) {
        Repair capture = REPAIR.get();
        if (capture == null || stack != capture.carrier) { return false; }
        if (amount >= 0 || entity != capture.player) { throw new IllegalStateException("Invalid native repair delta/actor"); }
        capture.calls++;
        capture.amount = Math.addExact(capture.amount, -(long) amount);
        return true;
    }

    public static int damage(ItemStack root, int amount, LivingEntity entity) {
        Api binding = api;
        if (binding == null || wearDisabled || amount <= 0 || !eligible(root) || !root.isDamageableItem()
                || entity != null && (entity.level().isClientSide() || entity.hasInfiniteMaterials())) { return amount; }
        try {
            var calls = binding.calls(root, false);
            if (calls.isEmpty()) { return amount; }
            ItemStack carrier = SilentGearCompatibility.carrier(root), before = carrier.copy();
            if (carrier.isEmpty()) { throw new IllegalStateException("Missing wear carrier"); }
            int result = amount;
            for (Call call : calls) {
                // Native GearHelper creates wear contexts with player=null, including for player-owned gear.
                int next = (int) binding.wear.damage.invoke(call.effect, binding.context.invoke(null, call.trait.nativeInstance(), carrier), result);
                if (next < 0 || Math.abs((long) next - result) > 1 || !ItemStack.matches(before, carrier)) {
                    throw new IllegalStateException("Native wear exceeded inspected delta/read-only contract");
                }
                result = next;
            }
            return result; // Vanilla applies Unbreaking, creative exemptions and ordinary destruction afterwards.
        } catch (Throwable failure) {
            disable(false, "native durability contract unavailable", failure);
            return amount;
        }
    }

    private static boolean eligible(ItemStack root) {
        var equippable = root.get(DataComponents.EQUIPPABLE);
        return root.getCount() == 1 && root.has(SilentGearCompatibility.state())
                && equippable != null && equippable.slot() == EquipmentSlot.FEET;
    }

    private static synchronized void disable(boolean repair, String reason, Throwable failure) {
        if (repair ? repairDisabled : wearDisabled) { return; }
        if (repair) { repairDisabled = true; } else { wearDisabled = true; }
        Constants.LOG.warn("Silent Gear mixed {} disabled: {}", repair ? "repair" : "wear", reason, failure);
    }

    private static final class Repair {
        private final ItemStack carrier;
        private final Player player;
        private long amount;
        private int calls;
        private Repair(ItemStack carrier, Player player) { this.carrier = carrier; this.player = player; }
    }

    private record Call(SilentGearCompatibility.Trait trait, Object effect) {}
    private record RepairApi(Class<?> type, MethodHandle tick, MethodHandle amount) {}
    private record WearApi(Class<?> type, MethodHandle damage, MethodHandle scale) {}

    private static final class Api {
        private final RepairApi repair;
        private final WearApi wear;
        private final MethodHandle context, definition, effects;

        private Api() throws ReflectiveOperationException {
            var lookup = MethodHandles.publicLookup();
            Class<?> instance = nativeClass("api.traits.TraitInstance"), trait = nativeClass("gear.trait.Trait"), ctx = nativeClass("api.traits.TraitActionContext");
            context = lookup.findConstructor(ctx, MethodType.methodType(void.class, Player.class, instance, ItemStack.class));
            definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(trait));
            effects = lookup.findVirtual(trait, "getEffects", MethodType.methodType(List.class));
            RepairApi repairBinding = null;
            try {
                Class<?> type = nativeClass("gear.trait.effect.SelfRepairTraitEffect");
                repairBinding = new RepairApi(type, lookup.findVirtual(type, "inventoryTick", MethodType.methodType(void.class, ctx, Level.class, Entity.class, boolean.class)),
                        MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findGetter(type, "repairAmount", int.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { disable(true, "optional repair API unavailable", failure); }
            repair = repairBinding;
            WearApi wearBinding = null;
            try {
                Class<?> type = nativeClass("gear.trait.effect.DurabilityTraitEffect");
                wearBinding = new WearApi(type, lookup.findVirtual(type, "onDurabilityDamage", MethodType.methodType(int.class, ctx, int.class)),
                        MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findGetter(type, "effectScale", int.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { disable(false, "optional wear API unavailable", failure); }
            wear = wearBinding;
        }

        private List<Call> calls(ItemStack root, boolean repairing) throws Throwable {
            var result = new ArrayList<Call>();
            for (var trait : SilentGearCompatibility.effectiveTraits(root)) {
                for (Object effect : (List<?>) effects.invoke(definition.invoke(trait.nativeInstance()))) {
                    if (!(repairing ? repair.type.isInstance(effect) : wear.type.isInstance(effect))) { continue; }
                    if (!(effect instanceof SilentGearEffectReadiness proof)
                            || !(repairing ? proof.mismatchedboots$sgRepairReady() : proof.mismatchedboots$sgWearReady())) {
                        throw new IllegalStateException("Final local effect proof unavailable");
                    }
                    if (repairing) {
                        int base = (int) repair.amount.invoke(effect);
                        if (base < 1 || (long) base * trait.level() > Integer.MAX_VALUE) { throw new IllegalStateException("Unsupported native repair amount"); }
                    } else if (Math.abs((long) (int) wear.scale.invoke(effect)) != 1) { throw new IllegalStateException("Unsupported native wear scale"); }
                    result.add(new Call(trait, effect));
                }
            }
            return result;
        }
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + name);
    }
}
