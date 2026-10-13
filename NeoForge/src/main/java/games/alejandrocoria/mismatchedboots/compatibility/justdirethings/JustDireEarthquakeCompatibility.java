package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One live root owns Eclipse eligibility, per-target payment and the two-phase worn clock. */
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
            var methods = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods");
            var eclipse = BuiltInRegistries.ITEM.get(id("eclipsealloy_boots")).orElseThrow().value();
            if (!tool.isInstance(eclipse) || !timer.isRecord()) { throw new IllegalStateException("Unsupported Earthquake boot/timer API"); }
            Api binding = new Api(eclipse, ability.getField("EARTHQUAKE").get(null),
                    (Enum<?>) ability.getField("GROUNDSTOMP").get(null),
                    lookup.findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                            .asType(MethodType.methodType(Set.class, Item.class)),
                    lookup.findVirtual(tool, "getAbilities", MethodType.methodType(java.util.EnumSet.class))
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
                    lookup.findStatic(methods, "isValidEarthquake", MethodType.methodType(boolean.class, Entity.class)),
                    component("ability_cooldowns"), scalar("earthquake_upgrade_installed", false),
                    scalar("tool_enabled", true), scalar("earthquake_toggle", true), scalar("forge_energy", 0),
                    scalar("earthquake_bindingmode", 0), scalar("earthquake_custom_setting", 0));
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
        return quote(root, true);
    }

    private static Quote quote(ItemStack root, boolean checkLockout) {
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
            if (checkLockout && pending > 0) {
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

    static boolean groundStompFirst() {
        Api binding = api;
        return binding == null || binding.stomp.ordinal() < ((Enum<?>) binding.ability).ordinal();
    }

    static int cooldownDuration() {
        Api binding = api;
        if (binding == null) { return -1; }
        try { return binding.parameters().cooldownTicks; }
        catch (Throwable failure) { disable(binding, failure); return -1; }
    }

    /** Settings admission, independent of FE/global enablement and spent clocks. */
    static boolean hasSettings(ItemStack root) {
        Api binding = api;
        if (binding == null || !JustDireSettingsCompatibility.mixed(root)) { return false; }
        try {
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            return (binding.permanent(data.left().source()) || binding.permanent(data.right().source()))
                    && ((Set<?>) binding.abilities.invokeExact(binding.eclipse)).contains(binding.ability);
        } catch (Throwable failure) { disable(binding, failure); return false; }
    }

    static Item settingsItem(ItemStack root) {
        Api binding = api;
        return binding != null && hasSettings(root) && api == binding ? binding.eclipse : null;
    }

    static Object settingAbility() { Api binding = api; return binding == null ? null : binding.ability; }

    static boolean edit(ItemStack root, JustDireActiveNetwork.SettingsRequest request) {
        Api binding = api;
        if (!hasSettings(root) || api != binding || !root.getOrDefault(binding.installed, false)) { return false; }
        switch (request.operation()) {
            case JustDireActiveNetwork.TOGGLE -> root.set(binding.toggle, !root.getOrDefault(binding.toggle, true));
            case JustDireActiveNetwork.PARTICLES -> {
                if (request.value() < 0 || request.value() > 1) { return false; }
                root.set(binding.particles, request.value());
            }
            case JustDireActiveNetwork.BINDING -> {
                return JustDireSettingsCompatibility.editEarthquakeBinding(root, request, binding.mode);
            }
            default -> { return false; } // The native fixed numeric value is not a slider or action control.
        }
        return true;
    }

    /** Only the consumed server dispatcher calls this boundary. expected advances by own commits, never by copying callbacks. */
    static boolean activate(ServerPlayer player, ItemStack root, ItemStack expected, int key, boolean mouse) {
        Api binding = api;
        if (binding == null || !JustDireGroundStompCompatibility.equipped(player, root) || !ItemStack.matches(root, expected)
                || player.containerMenu != player.inventoryMenu || !JustDireSettingsCompatibility.earthquakeBinding(root, key, mouse)) { return false; }
        Quote initial;
        List<Object> next;
        List<Mob> targets;
        int particles, bill;
        GameType mode;
        try {
            initial = quote(root);
            if (initial == null || api != binding) { return false; }
            particles = root.getOrDefault(binding.particles, 0);
            int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING));
            if (particles < 0 || particles > 1 || unbreaking < 0) { return false; }
            mode = player.gameMode.getGameModeForPlayer();
            bill = mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 0
                    : (int) Math.max(0, initial.baseCost - initial.baseCost * Math.min(1, unbreaking * .1));
            next = new ArrayList<>(root.getOrDefault(binding.timers, List.of()));
            next.add(binding.create.invokeExact("earthquake", initial.activeTicks, true));
            targets = player.level().getEntitiesOfClass(Mob.class,
                    new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                            player.getX() + 5, player.getY() + 5, player.getZ() + 5), binding::validTarget);
        } catch (Throwable failure) { disable(binding, failure); return false; }
        if (api != binding || !JustDireGroundStompCompatibility.equipped(player, root) || !ItemStack.matches(root, expected)) { return false; }
        // Native earthquake returns false even after effects/payment. It is not a commit receipt.
        // Start lockout even for empty targets; no catch below spans payment or world mutation.
        root.set(binding.timers, List.copyOf(next)); expected.set(binding.timers, List.copyOf(next));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1, .5f);
        for (Mob mob : targets) {
            if (api != binding || !JustDireGroundStompCompatibility.equipped(player, root) || !ItemStack.matches(root, expected)
                    || player.gameMode.getGameModeForPlayer() != mode) { break; }
            // Our own new active record is expected; recheck native controls and the full base FE budget, not cooldown admission.
            if (!initial.equals(quote(root, false))) { break; }
            if (bill > 0) {
                var access = ItemAccess.forStack(root);
                var energy = access.getCapability(Capabilities.Energy.ITEM);
                if (energy == null || energy.getAmountAsInt() < initial.baseCost) { break; }
                var afterDebit = expected.copy(); afterDebit.set(binding.energy, energy.getAmountAsInt() - bill);
                try (var transaction = Transaction.openRoot()) {
                    if (energy.extract(bill, transaction) != bill || !JustDireGroundStompCompatibility.equipped(player, root)
                            || !ItemStack.matches(root, expected) || !ItemStack.matches(access.getResource().toStack(), afterDebit)) { break; }
                    transaction.commit();
                }
                expected.set(binding.energy, afterDebit.get(binding.energy));
            }
            if (!JustDireGroundStompCompatibility.equipped(player, root) || !ItemStack.matches(root, expected)) { break; }
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3), mob);
            if (particles == 0) {
                player.level().sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY(), mob.getZ(), 20, .25, .2, .25, 0);
                player.level().sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY(), mob.getZ(), 20, .5, .2, .5, 0);
            }
        }
        return true; // A spent lockout remains accepted even if a later target debit fails.
    }

    static void tickEquipped(ServerPlayer player) {
        Api binding = api;
        var root = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if (binding == null || !JustDireGroundStompCompatibility.equipped(player, root)) { return; }
        var next = new ArrayList<Object>();
        boolean changed = false;
        try {
            Object pending = null;
            int count = 0;
            for (Object record : root.getOrDefault(binding.timers, List.of())) {
                if (binding.earthquake(record)) { pending = record; count++; }
            }
            if (count == 0) { return; }
            if (count > 1 || !binding.pending(pending).supported()) { invalidTimer(); return; }
            int cooldown = binding.parameters().cooldownTicks;
            for (Object record : root.get(binding.timers)) {
                if (!binding.earthquake(record)) { next.add(record); continue; }
                Pending clock = binding.pending(record);
                if (clock.ticks > 1) { next.add(binding.create.invokeExact("earthquake", clock.ticks - 1, clock.active)); }
                else if (clock.active) { next.add(binding.create.invokeExact("earthquake", cooldown, false)); }
                changed = true;
            }
        } catch (Throwable failure) { disable(binding, failure); return; }
        if (changed && api == binding && JustDireGroundStompCompatibility.equipped(player, root)) { root.set(binding.timers, List.copyOf(next)); }
    }

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
        var json = sample instanceof Boolean value ? new JsonPrimitive(value) : new JsonPrimitive((Integer) sample);
        if (type.codec() == null || type.streamCodec() == null
                || !sample.getClass().isInstance(type.codec().parse(JsonOps.INSTANCE, json).getOrThrow())) {
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
    private record Api(Item eclipse, Object ability, Enum<?> stomp, MethodHandle allAbilities, MethodHandle abilities, MethodHandle usable, MethodHandle params,
                       MethodHandle activeDuration, MethodHandle cooldownDuration, MethodHandle cost, MethodHandle name,
                       MethodHandle ticks, MethodHandle active, MethodHandle create, MethodHandle filter, DataComponentType<List<Object>> timers,
                       DataComponentType<Boolean> installed, DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle,
                       DataComponentType<Integer> energy, DataComponentType<Integer> mode, DataComponentType<Integer> particles) {
        boolean validTarget(Entity entity) {
            try { return (boolean) filter.invokeExact(entity); }
            catch (Throwable failure) {
                if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
                throw new IllegalStateException("Earthquake target query failed", failure);
            }
        }
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
