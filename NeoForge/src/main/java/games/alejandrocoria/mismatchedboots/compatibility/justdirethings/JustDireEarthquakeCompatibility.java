package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Creation-only lockout import and read-only eligibility; no action, tick or input admission. */
public final class JustDireEarthquakeCompatibility {
    private static volatile Api api;
    private static boolean invalidTimerWarned;

    private JustDireEarthquakeCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireEarthquakeCompatibility::bind); }
    }

    public static boolean available() { return api != null; }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var lookup = MethodHandles.publicLookup();
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var timer = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
            var eclipse = BuiltInRegistries.ITEM.get(id("eclipsealloy_boots")).orElseThrow().value();
            if (!tool.isInstance(eclipse) || !timer.isRecord()) { throw new IllegalStateException("Unsupported Earthquake boot/timer API"); }
            Api binding = new Api(eclipse, ability.getField("EARTHQUAKE").get(null),
                    lookup.findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                            .asType(MethodType.methodType(Set.class, Item.class)),
                    lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, ItemStack.class, Object.class)),
                    lookup.findVirtual(tool, "getAbilityParams", MethodType.methodType(params, ability))
                            .asType(MethodType.methodType(Object.class, Item.class, Object.class)),
                    getter(lookup, params, "activeCooldown"), getter(lookup, params, "cooldown"),
                    lookup.findVirtual(ability, "getFeCost", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(timer, "abilityName", MethodType.methodType(String.class))
                            .asType(MethodType.methodType(String.class, Object.class)),
                    lookup.findVirtual(timer, "cooldownTicks", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(timer, "isactive", MethodType.methodType(boolean.class))
                            .asType(MethodType.methodType(boolean.class, Object.class)),
                    lookup.findConstructor(timer, MethodType.methodType(void.class, String.class, int.class, boolean.class))
                            .asType(MethodType.methodType(Object.class, String.class, int.class, boolean.class)),
                    component("ability_cooldowns"), scalar("earthquake_upgrade_installed", false),
                    scalar("tool_enabled", true), scalar("earthquake_toggle", true));
            if (binding.timers.codec() == null || binding.timers.streamCodec() == null) {
                throw new IllegalStateException("Unsupported Earthquake timer codecs");
            }
            Object decoded = binding.timers.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "[{\"ability_name\":\"earthquake\",\"cooldown_ticks\":1,\"is_active\":true}]")).getOrThrow();
            if (!(decoded instanceof List<?> records) || records.size() != 1 || !timer.isInstance(records.getFirst())) {
                throw new IllegalStateException("Unsupported Earthquake timer value");
            }
            api = binding;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things Earthquake API; Earthquake compatibility disabled", failure);
        }
    }

    public static void initializeCooldown(ItemStack output) {
        Api binding = api;
        if (binding == null) { return; }
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        List<Object> imported = new ArrayList<>();
        try {
            // This replaces only Earthquake's initial projection, after Ground Stomp imports its own timer.
            for (Object record : output.getOrDefault(binding.timers, List.of())) {
                if (!binding.earthquake(record)) { imported.add(binding.copy(record)); }
            }
            var parameters = binding.parameters();
            Pending selected = null;
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (!binding.permanent(source)) { continue; }
                var records = new ArrayList<Object>();
                for (Object record : source.getOrDefault(binding.timers, List.of())) {
                    if (binding.earthquake(record)) { records.add(record); }
                }
                // Multiple records within one input are ambiguous, not independent clocks to merge.
                if (records.size() > 1) {
                    invalidTimer();
                    for (Object record : records) { imported.add(binding.copy(record)); }
                } else if (!records.isEmpty()) {
                    Object record = records.getFirst();
                    Pending pending = binding.pending(record);
                    if (!pending.supported()) { invalidTimer(); imported.add(binding.copy(record)); }
                    else if (pending.preferredTo(selected, parameters.cooldownTicks)) { selected = pending; }
                }
            }
            if (selected != null) { imported.add(binding.create.invokeExact("earthquake", selected.ticks, selected.active)); }
        } catch (Throwable failure) { disable(binding, failure); return; }
        if (api != binding || imported.isEmpty() && !output.has(binding.timers)) { return; }
        output.set(binding.timers, List.copyOf(imported));
    }

    /** Eligibility only. Native usability excludes resource/payment checks; the live root owns the base FE budget. */
    public static Quote quote(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return null; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return null; }
        var left = data.left().source(); var right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return null; }
        Api binding = api;
        if (binding == null) { return null; }
        try {
            if (!root.getOrDefault(binding.installed, false) || !root.getOrDefault(binding.enabled, true)
                    || !root.getOrDefault(binding.toggle, true)) { return null; }
            int pending = 0;
            boolean unsupported = false;
            for (Object record : root.getOrDefault(binding.timers, List.of())) {
                if (binding.earthquake(record)) { pending++; unsupported |= !binding.pending(record).supported(); }
            }
            if (pending > 0) {
                if (unsupported || pending > 1) { invalidTimer(); }
                return null; // Native -1 conflates absence with a stored counter: inspect records instead.
            }
            for (var source : List.of(left, right)) {
                if (!binding.permanent(source)) { continue; }
                // source() is defensive. Historical installation, charge and unused numeric values are not permission.
                source.set(binding.installed, true);
                source.set(binding.enabled, true);
                source.set(binding.toggle, true);
                if (!(boolean) binding.usable.invokeExact(source.getItem(), source, binding.ability)) { continue; }
                var parameters = binding.parameters();
                int base = (int) binding.cost.invokeExact(binding.ability);
                if (base <= 0) { throw new IllegalStateException("Unsupported native Earthquake FE cost"); }
                var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
                if (energy == null || energy.getAmountAsInt() < base) { return null; }
                return api == binding ? new Quote(base, parameters.activeTicks, parameters.cooldownTicks) : null;
            }
            return null;
        } catch (Throwable failure) { disable(binding, failure); return null; }
    }

    public record Quote(int baseCost, int activeTicks, int cooldownTicks) {}

    /** Positive native counters have no codec upper bound; long accounting must not wrap large pending lockouts. */
    record Pending(int ticks, boolean active) {
        boolean supported() { return ticks > 0; }
        long remaining(int cooldown) { return (long) ticks + (active ? cooldown : 0); }
        boolean preferredTo(Pending other, int cooldown) {
            return other == null || remaining(cooldown) > other.remaining(cooldown)
                    || remaining(cooldown) == other.remaining(cooldown) && active && !other.active;
        }
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String field) throws ReflectiveOperationException {
        return lookup.findGetter(owner, field, int.class).asType(MethodType.methodType(int.class, Object.class));
    }

    private static <T> DataComponentType<T> scalar(String path, T sample) {
        DataComponentType<T> type = component(path);
        if (type.codec() == null || type.streamCodec() == null
                || !sample.getClass().isInstance(type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive((Boolean) sample)).getOrThrow())) {
            throw new IllegalStateException("Unsupported Earthquake component: " + path);
        }
        return type;
    }

    @SuppressWarnings("unchecked") private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).orElseThrow().value();
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("justdirethings", path); }

    private static synchronized void invalidTimer() {
        if (!invalidTimerWarned) {
            invalidTimerWarned = true;
            Constants.LOG.warn("Just Dire Things Earthquake retained an unsupported cooldown; activation denied");
        }
    }
    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        synchronized (JustDireEarthquakeCompatibility.class) {
            if (api == binding) {
                api = null;
                Constants.LOG.warn("Just Dire Things Earthquake queries failed; Earthquake compatibility disabled", failure);
            }
        }
    }

    private record Parameters(int activeTicks, int cooldownTicks) {}
    private record Api(Item eclipse, Object ability, MethodHandle allAbilities, MethodHandle usable, MethodHandle params,
                       MethodHandle activeDuration, MethodHandle cooldownDuration, MethodHandle cost, MethodHandle name,
                       MethodHandle ticks, MethodHandle active, MethodHandle create, DataComponentType<List<Object>> timers,
                       DataComponentType<Boolean> installed, DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle) {
        boolean permanent(ItemStack source) throws Throwable {
            return source.is(eclipse) && ((Set<?>) allAbilities.invokeExact(source.getItem())).contains(ability);
        }
        boolean earthquake(Object record) throws Throwable { return "earthquake".equals((String) name.invokeExact(record)); }
        Pending pending(Object record) throws Throwable { return new Pending((int) ticks.invokeExact(record), (boolean) active.invokeExact(record)); }
        Object copy(Object record) throws Throwable {
            return create.invokeExact((String) name.invokeExact(record), (int) ticks.invokeExact(record), (boolean) active.invokeExact(record));
        }
        Parameters parameters() throws Throwable {
            Object values = params.invokeExact(eclipse, ability);
            int active = (int) activeDuration.invokeExact(values), cooldown = (int) cooldownDuration.invokeExact(values);
            if (active <= 0 || cooldown <= 0) { throw new IllegalStateException("Unsupported native Earthquake durations"); }
            return new Parameters(active, cooldown);
        }
    }
}
