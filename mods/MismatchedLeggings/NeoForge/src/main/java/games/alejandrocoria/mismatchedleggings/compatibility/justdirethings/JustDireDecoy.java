package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native identity and entity, with a single live-root wallet and worn-only two-phase clock. */
public final class JustDireDecoy {
    private static final AtomicBoolean INVALID_WARNED = new AtomicBoolean();
    private static final AtomicBoolean SOUND_WARNED = new AtomicBoolean();
    private static volatile Api api;
    private static volatile boolean soundFailed;

    private JustDireDecoy() {}

    public static boolean available() {
        return api != null;
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) {
            bus.addListener((RegisterCapabilitiesEvent event) -> bind());
        }
    }

    private static void bind() {
        try {
            var lookup = MethodHandles.publicLookup();
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
            var entity = Class.forName("com.direwolf20.justdirethings.common.entities.DecoyEntity");
            var helpers = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Helpers");
            DataComponentType<List<Object>> clocks = JustDireState.component("ability_cooldowns");
            if (!record.isRecord() || !Entity.class.isAssignableFrom(entity) || clocks.codec() == null || clocks.streamCodec() == null) {
                throw new IllegalStateException("Unsupported native Decoy data API");
            }
            Object decoded = clocks.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "[{\"ability_name\":\"decoy\",\"cooldown_ticks\":200,\"is_active\":true}]")).getOrThrow();
            if (!(decoded instanceof List<?> values) || values.size() != 1 || !record.isInstance(values.getFirst())) {
                throw new IllegalStateException("Unsupported native Decoy clock codec");
            }
            var celestigem = BuiltInRegistries.ITEM.get(JustDireState.id("celestigem_leggings")).orElseThrow().value();
            var eclipse = BuiltInRegistries.ITEM.get(JustDireState.id("eclipsealloy_leggings")).orElseThrow().value();
            if (!tool.isInstance(celestigem) || !tool.isInstance(eclipse)) {
                throw new IllegalStateException("Unsupported native Decoy providers");
            }
            Object decoy = ability.getField("DECOY").get(null);
            var binding = new Api(celestigem, eclipse, decoy, record, clocks,
                    lookup.findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                            .asType(MethodType.methodType(Set.class, Item.class)),
                    lookup.findVirtual(tool, "getAbilityParams", MethodType.methodType(params, ability))
                            .asType(MethodType.methodType(Object.class, Item.class, Object.class)),
                    lookup.findVirtual(tool, "canUseAbilityAndDurability", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, ItemStack.class, Object.class)),
                    lookup.findVirtual(ability, "getFeCost", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(params, "activeCooldown", int.class).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(params, "cooldown", int.class).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(record, "abilityName", MethodType.methodType(String.class)).asType(MethodType.methodType(String.class, Object.class)),
                    lookup.findVirtual(record, "cooldownTicks", MethodType.methodType(int.class)).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(record, "isactive", MethodType.methodType(boolean.class)).asType(MethodType.methodType(boolean.class, Object.class)),
                    lookup.findConstructor(record, MethodType.methodType(void.class, String.class, int.class, boolean.class))
                            .asType(MethodType.methodType(Object.class, String.class, int.class, boolean.class)),
                    lookup.findConstructor(entity, MethodType.methodType(void.class, Level.class)).asType(MethodType.methodType(Entity.class, Level.class)),
                    lookup.findVirtual(entity, "setSummonerName", MethodType.methodType(void.class, String.class))
                            .asType(MethodType.methodType(void.class, Entity.class, String.class)),
                    lookup.findVirtual(entity, "setOwnerUUID", MethodType.methodType(void.class, UUID.class))
                            .asType(MethodType.methodType(void.class, Entity.class, UUID.class)),
                    lookup.findStatic(helpers, "playSoundToAll", MethodType.methodType(void.class, Player.class, SoundEvent.class, float.class, float.class)));
            // Exercise the constructors/accessors before publishing a binding used across payment commit.
            var probe = read(values, binding);
            if (!probe.valid || probe.own == null || probe.own.ticks != 200 || !probe.own.active) {
                throw new IllegalStateException("Unsupported native Decoy record semantics");
            }
            clock(binding, 200, true);
            profile(celestigem, binding);
            profile(eclipse, binding);
            api = binding;
        } catch (Throwable failure) {
            fatal(failure);
            LeggingsContent.LOG.warn("Could not bind Just Dire Things leggings Decoy; action and clock disabled", failure);
        }
    }

    /** Creation only: both inverse outputs inherit the greatest effective-tier waiting time. */
    static void prepare(ItemStack output, MixedLeggingsData data) {
        var sources = List.of(data.left().source(), data.right().source());
        Api binding = api;
        if (binding == null) {
            if (sources.stream().anyMatch(JustDireAbilities::powered)) {
                throw new IllegalArgumentException("Cannot import Decoy clocks without their native API");
            }
            return;
        }
        try {
            Profile profile = profile(sources, binding);
            if (profile == null) { return; }
            var inherited = new ArrayList<Object>();
            Clock selected = null;
            boolean invalid = false;
            for (var source : sources) {
                if (!provider(source, binding)) { continue; }
                var stored = records(source, binding);
                var state = read(stored, binding);
                if (!state.valid) { invalid = true; }
                for (Object value : stored) {
                    if (name(value, binding).equals("decoy")) { inherited.add(value); }
                }
                if (state.own != null && (selected == null || later(state.own, selected, profile.cooldown))) {
                    selected = state.own;
                }
            }
            var next = new ArrayList<Object>();
            for (Object value : records(output, binding)) {
                if (!name(value, binding).equals("decoy")) { next.add(value); }
            }
            // Preserve malformed own records as a lockout, never convert corrupt history into readiness.
            if (invalid) { next.addAll(inherited); }
            else if (selected != null) { next.add(clock(binding, selected.ticks, selected.active)); }
            write(output, binding, List.copyOf(next));
        } catch (Throwable failure) {
            fatal(failure);
            disable(binding, failure);
            throw new IllegalArgumentException("Cannot import Just Dire Things Decoy clock", failure);
        }
    }

    private static boolean later(Clock candidate, Clock selected, int cooldown) {
        long a = (long) candidate.ticks + (candidate.active ? cooldown : 0);
        long b = (long) selected.ticks + (selected.active ? cooldown : 0);
        return a > b || a == b && candidate.active && !selected.active;
    }

    /** Internal gameplay action. The input route must first authorize the real binding and location. */
    public static boolean activate(ServerPlayer player, int slot, ItemStack expected) {
        Api binding = api;
        if (binding == null || slot < 0 || slot >= player.getInventory().getContainerSize()
                || player.getInventory().getItem(slot) != expected || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu) { return false; }
        Prepared prepared;
        try {
            Profile profile = profile(expected, binding);
            if (profile == null) { return false; }
            var records = records(expected, binding);
            var state = read(records, binding);
            if (!state.valid || state.own != null) { return false; }
            var view = new ItemStack(profile.item);
            view.applyComponents(expected.getComponents());
            if (!(boolean) binding.usable.invokeExact(profile.item, view, binding.ability)) { return false; }
            int cost = (int) binding.cost.invokeExact(binding.ability);
            if (cost <= 0) { throw new IllegalStateException("Invalid native Decoy FE cost"); }
            int unbreaking = expected.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING));
            int bill = player.isCreative() ? 0 : (int) Math.max(0, cost - cost * Math.min(1, unbreaking * .1));
            var next = new ArrayList<>(records);
            next.add(clock(binding, profile.active, true));
            var entity = (Entity) binding.entity.invokeExact((Level) player.level());
            entity.setPos(player.position());
            binding.summoner.invokeExact(entity, player.getName().getString());
            binding.owner.invokeExact(entity, player.getUUID());
            prepared = new Prepared(entity, bill, List.copyOf(next), expected.copy());
        } catch (Throwable failure) {
            fatal(failure);
            disable(binding, failure);
            return false;
        }
        var wallet = ItemAccess.forStack(expected).getCapability(Capabilities.Energy.ITEM);
        if (wallet == null) { return false; }

        // Native decoy() returns false even on success and ignores insertion failure; a copy cannot own this payment.
        boolean committed = false;
        try (var transaction = Transaction.openRoot()) {
            try {
                if (wallet.extract(prepared.bill, transaction) != prepared.bill || !current(player, slot, expected, prepared.snapshot)) { return false; }
                if (!player.level().addFreshEntity(prepared.entity) || !current(player, slot, expected, prepared.snapshot)) { return false; }
                transaction.commit();
                committed = true;
            } finally {
                if (!committed) { prepared.entity.discard(); }
            }
        }
        // No catch/retry straddles commit. Record allocation/native calls already succeeded before payment.
        write(expected, binding, prepared.records);
        sound(player, SoundEvents.EVOKER_PREPARE_SUMMON, binding);
        return true;
    }

    private static boolean current(ServerPlayer player, int slot, ItemStack root, ItemStack snapshot) {
        return player.getInventory().getItem(slot) == root && ItemStack.matches(root, snapshot);
    }

    /** Called only from the mixed item's real worn inventory tick, never from arbitrary stored stacks. */
    public static void tick(Player player) {
        Api binding = api;
        if (binding == null || !(player instanceof ServerPlayer)) { return; }
        var root = player.getItemBySlot(EquipmentSlot.LEGS);
        List<Object> next;
        SoundEvent sound = null;
        try {
            Profile profile = profile(root, binding);
            if (profile == null) { return; }
            var stored = records(root, binding);
            var state = read(stored, binding);
            if (!state.valid || state.own == null) { return; }
            var result = new ArrayList<Object>();
            for (Object value : stored) {
                if (!name(value, binding).equals("decoy")) { result.add(value); }
                else if (state.own.ticks > 1) { result.add(clock(binding, state.own.ticks - 1, state.own.active)); }
                else if (state.own.active) {
                    result.add(clock(binding, profile.cooldown, false));
                    sound = SoundEvents.CONDUIT_DEACTIVATE;
                } else { sound = SoundEvents.ENDER_EYE_DEATH; }
            }
            next = List.copyOf(result);
        } catch (Throwable failure) {
            fatal(failure);
            disable(binding, failure);
            return;
        }
        write(root, binding, next);
        if (sound != null) { sound(player, sound, binding); }
    }

    private static @Nullable Profile profile(ItemStack root, Api binding) throws Throwable {
        var data = root.getCount() == 1 ? MixedLeggingsStructure.validData(root).orElse(null) : null;
        return data == null ? null : profile(List.of(data.left().source(), data.right().source()), binding);
    }

    private static @Nullable Profile profile(List<ItemStack> sources, Api binding) throws Throwable {
        Item selected = null;
        for (var source : sources) {
            if (provider(source, binding) && (selected == null || source.is(binding.eclipse))) { selected = source.getItem(); }
        }
        return selected == null ? null : profile(selected, binding);
    }

    private static Profile profile(Item item, Api binding) throws Throwable {
        Set<?> abilities = (Set<?>) binding.all.invokeExact(item);
        if (!abilities.contains(binding.ability)) { throw new IllegalStateException("Missing native Decoy entitlement"); }
        Object params = (Object) binding.params.invokeExact(item, binding.ability);
        int active = (int) binding.active.invokeExact(params);
        int cooldown = (int) binding.cooldown.invokeExact(params);
        if (active <= 0 || cooldown <= 0) { throw new IllegalStateException("Invalid native Decoy durations"); }
        return new Profile(item, active, cooldown);
    }

    private static boolean provider(ItemStack source, Api binding) {
        return source.getCount() == 1 && (source.is(binding.celestigem) || source.is(binding.eclipse));
    }

    private static List<Object> records(ItemStack root, Api binding) {
        return root.getOrDefault(binding.clocks, List.of());
    }

    private static String name(Object value, Api binding) throws Throwable {
        if (!binding.record.isInstance(value)) { throw new IllegalStateException("Unsupported native cooldown entry"); }
        String name = (String) binding.name.invokeExact(value);
        if (name == null) { throw new IllegalStateException("Missing native cooldown name"); }
        return name;
    }

    private static State read(List<?> records, Api binding) throws Throwable {
        Clock own = null;
        boolean valid = true;
        for (Object value : records) {
            if (!name(value, binding).equals("decoy")) { continue; }
            int ticks = (int) binding.ticks.invokeExact(value);
            if (own != null || ticks <= 0) { valid = false; }
            own = new Clock(ticks, (boolean) binding.isActive.invokeExact(value));
        }
        if (!valid && INVALID_WARNED.compareAndSet(false, true)) {
            LeggingsContent.LOG.warn("Invalid or duplicate Just Dire Things Decoy clock; preserving data and refusing leggings action/tick");
        }
        return new State(valid, own);
    }

    private static Object clock(Api binding, int ticks, boolean active) throws Throwable {
        return (Object) binding.newClock.invokeExact("decoy", ticks, active);
    }

    private static void write(ItemStack root, Api binding, List<Object> records) {
        if (records.isEmpty()) { root.remove(binding.clocks); }
        else { root.set(binding.clocks, records); }
    }

    private static void sound(Player player, SoundEvent sound, Api binding) {
        if (soundFailed) { return; }
        try {
            binding.sound.invokeExact(player, sound, 1f, 1f);
        } catch (Throwable failure) {
            fatal(failure);
            soundFailed = true;
            if (SOUND_WARNED.compareAndSet(false, true)) {
                LeggingsContent.LOG.warn("Just Dire Things Decoy feedback failed; leggings sounds disabled without retrying action", failure);
            }
        }
    }

    private static void disable(Api binding, Throwable failure) {
        synchronized (JustDireDecoy.class) {
            if (api == binding) {
                api = null;
                LeggingsContent.LOG.warn("Just Dire Things Decoy API failed; leggings action/clock disabled without discarding records", failure);
            }
        }
    }

    private static void fatal(Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
    }

    private record Profile(Item item, int active, int cooldown) {}
    private record Clock(int ticks, boolean active) {}
    private record State(boolean valid, @Nullable Clock own) {}
    private record Prepared(Entity entity, int bill, List<Object> records, ItemStack snapshot) {}
    private record Api(Item celestigem, Item eclipse, Object ability, Class<?> record, DataComponentType<List<Object>> clocks,
                       MethodHandle all, MethodHandle params, MethodHandle usable, MethodHandle cost,
                       MethodHandle active, MethodHandle cooldown, MethodHandle name, MethodHandle ticks, MethodHandle isActive,
                       MethodHandle newClock, MethodHandle entity, MethodHandle summoner, MethodHandle owner, MethodHandle sound) {}
}
