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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** One live root owns quotes, payment and supported timers; native query copies never become wallets. */
public final class JustDireGroundStompCompatibility {
    private static volatile Api api;
    private static boolean invalidTimerWarned;
    private static final Set<ServerPlayer> executing = Collections.newSetFromMap(new IdentityHashMap<>());

    private JustDireGroundStompCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireGroundStompCompatibility::bind); }
    }

    public static boolean available() { return api != null; }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var lookup = MethodHandles.publicLookup();
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var timer = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
            // The native record is immutable; clone its records as well as the mutable list at import.
            if (!timer.isRecord()) { throw new IllegalStateException("Unsupported native cooldown record"); }
            var providers = new ArrayList<NativeProvider>();
            for (Provider provider : Provider.values()) {
                Item item = BuiltInRegistries.ITEM.get(id(provider.path + "_boots")).orElseThrow().value();
                if (!tool.isInstance(item)) { throw new IllegalStateException("Unsupported Ground Stomp boot API"); }
                providers.add(new NativeProvider(provider, item));
            }
            Api binding = new Api(ability.getField("GROUNDSTOMP").get(null), List.copyOf(providers),
                    lookup.findVirtual(tool, "getAllAbilities", MethodType.methodType(java.util.EnumSet.class))
                            .asType(MethodType.methodType(Set.class, Item.class)),
                    lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, ItemStack.class, Object.class)),
                    lookup.findStatic(tool, "getToolValue", MethodType.methodType(int.class, ItemStack.class, String.class)),
                    lookup.findVirtual(tool, "getAbilityParams", MethodType.methodType(params, ability))
                            .asType(MethodType.methodType(Object.class, Item.class, Object.class)),
                    getter(lookup, params, "minSlider", int.class), getter(lookup, params, "maxSlider", int.class),
                    getter(lookup, params, "defaultValue", int.class), getter(lookup, params, "cooldown", int.class),
                    lookup.findVirtual(ability, "getFeCost", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(ability, "getDurabilityCost", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(timer, "abilityName", MethodType.methodType(String.class))
                            .asType(MethodType.methodType(String.class, Object.class)),
                    lookup.findVirtual(timer, "cooldownTicks", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findVirtual(timer, "isactive", MethodType.methodType(boolean.class))
                            .asType(MethodType.methodType(boolean.class, Object.class)),
                    lookup.findConstructor(timer, MethodType.methodType(void.class, String.class, int.class, boolean.class))
                            .asType(MethodType.methodType(Object.class, String.class, int.class, boolean.class)),
                    component("ability_cooldowns"), scalar("groundstomp_upgrade_installed", false),
                    scalar("tool_enabled", true), scalar("groundstomp_toggle", true), scalar("groundstomp_value", 1), scalar("forge_energy", 0));
            if (binding.timers.codec() == null || binding.timers.streamCodec() == null) {
                throw new IllegalStateException("Unsupported native cooldown component codec");
            }
            Object decoded = binding.timers.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "[{\"ability_name\":\"groundstomp\",\"cooldown_ticks\":1,\"is_active\":false}]")).getOrThrow();
            if (!(decoded instanceof List<?> records) || records.size() != 1 || !timer.isInstance(records.getFirst())) {
                throw new IllegalStateException("Unsupported native cooldown component value");
            }
            api = binding;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things Ground Stomp API; active compatibility disabled", failure);
        }
    }

    public static void initializeCooldown(ItemStack output) {
        Api binding = api;
        if (binding == null) { return; }
        var data = ModContent.getMixedBootsData(output).orElseThrow();
        List<Object> imported;
        try {
            imported = new ArrayList<>();
            // Preserve the ordinary owner/config projection for every unrelated ability.
            for (Object record : output.getOrDefault(binding.timers, List.of())) {
                if (!binding.groundStomp(record)) { imported.add(binding.copy(record)); }
            }
            int maximum = 0;
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (binding.provider(source) == null || !binding.permanent(source)) { continue; }
                for (Object record : source.getOrDefault(binding.timers, List.of())) {
                    if (!binding.groundStomp(record)) { continue; }
                    int ticks = (int) binding.ticks.invokeExact(record);
                    boolean active = (boolean) binding.active.invokeExact(record);
                    if (active || ticks < 0) {
                        // Typed native codecs accept these shapes. Retain the lockout, not readiness
                        // or a fabricated active-to-cooldown transition for this non-active ability.
                        invalidTimer(); imported.add(binding.copy(record));
                    } else { maximum = Math.max(maximum, ticks); }
                }
            }
            if (maximum > 0) { imported.add(binding.create.invokeExact("groundstomp", maximum, false)); }
        } catch (Throwable failure) { disable(binding, failure); return; }
        if (api != binding) { return; }
        if (imported.isEmpty() && !output.has(binding.timers)) { return; }
        output.set(binding.timers, new ArrayList<>(imported));
    }

    /** A quote is not equipment identity or permission to commit; callers must revalidate live state. */
    public static Quote quote(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return null; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return null; }
        var left = data.left().source();
        var right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return null; }
        Api binding = api;
        if (binding == null) { return null; }
        try {
            if (!root.getOrDefault(binding.installed, false) || !root.getOrDefault(binding.enabled, true)
                    || !root.getOrDefault(binding.toggle, true)) { return null; }
            for (Object record : root.getOrDefault(binding.timers, List.of())) {
                if (binding.groundStomp(record)) {
                    if ((boolean) binding.active.invokeExact(record) || (int) binding.ticks.invokeExact(record) < 0) { invalidTimer(); }
                    return null; // Even a zero native record must be removed by the later supported tick, not by a query.
                }
            }
            Quote selected = null;
            for (ItemStack source : List.of(left, right)) {
                NativeProvider provider = binding.provider(source);
                if (provider == null || !binding.permanent(source)) { continue; }
                source.set(binding.installed, true);
                source.set(binding.enabled, root.getOrDefault(binding.enabled, true));
                source.set(binding.toggle, root.getOrDefault(binding.toggle, true));
                source.remove(binding.value);
                if (root.has(binding.value)) { source.set(binding.value, root.get(binding.value)); }
                if (!(boolean) binding.usable.invokeExact(source.getItem(), source, binding.ability)) { continue; }
                Object params = binding.params.invokeExact(source.getItem(), binding.ability);
                int min = (int) binding.minimum.invokeExact(params);
                int max = (int) binding.maximum.invokeExact(params);
                int defaultValue = (int) binding.defaultValue.invokeExact(params);
                int cooldown = (int) binding.cooldown.invokeExact(params);
                int base = provider.kind.powered ? (int) binding.feCost.invokeExact(binding.ability)
                        : (int) binding.durabilityCost.invokeExact(binding.ability);
                if (min <= 0 || max < min || defaultValue < min || defaultValue > max || cooldown <= 0 || base <= 0) {
                    throw new IllegalStateException("Unsupported native Ground Stomp parameters/cost");
                }
                if (provider.kind.powered) {
                    var energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
                    if (energy == null || energy.getAmountAsInt() < base) { continue; }
                } else {
                    // Native ability eligibility checks the base budget even for Unbreakable;
                    // actual hurtAndBreak/payment exemptions belong to the later commit boundary.
                    if ((long) root.getMaxDamage() - root.getDamageValue() < base) { continue; }
                }
                int strength = (int) binding.slider.invokeExact(source, "groundstomp");
                if (strength < min || strength > max) { throw new IllegalStateException("Unsupported native Ground Stomp value"); }
                Quote candidate = new Quote(provider.kind, strength, base, cooldown);
                if (candidate.preferredTo(selected)) { selected = candidate; }
            }
            return api == binding ? selected : null;
        } catch (Throwable failure) { disable(binding, failure); return null; }
    }

    /** Internal server action boundary. Wire callers must first consume their independent activation permit. */
    static boolean activate(ServerPlayer player, ItemStack root, ItemStack expected, int key, boolean mouse) {
        if (!executing.add(player)) { return false; }
        try {
            Api binding = api;
            if (binding == null || !equipped(player, root) || !ItemStack.matches(root, expected)
                    || player.containerMenu != player.inventoryMenu
                    || !JustDireSettingsCompatibility.groundStompBinding(root, key, mouse)) { return false; }
            Action action;
            try {
                Quote quote = quote(root);
                if (quote == null || api != binding) { return false; }
                int particles = JustDireSettingsCompatibility.particles(root);
                if (particles < 0 || particles > 1) { return false; }
                int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.UNBREAKING));
                if (unbreaking < 0) { return false; }
                GameType mode = player.gameMode.getGameModeForPlayer();
                int bill = mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 0 : quote.provider.powered
                        ? (int) Math.max(0, quote.baseCost - quote.baseCost * Math.min(1, unbreaking * .1)) : quote.baseCost;
                var next = new ArrayList<>(root.getOrDefault(binding.timers, List.of()));
                next.add(binding.create.invokeExact("groundstomp", quote.cooldownTicks, false));
                var targets = player.level().getEntitiesOfClass(Mob.class,
                        new AABB(player.getX() - 3, player.getY() - 3, player.getZ() - 3,
                                player.getX() + 3, player.getY() + 3, player.getZ() + 3),
                        JustDireGroundStompCompatibility::validTarget);
                ItemAccess access = quote.provider.powered ? ItemAccess.forStack(root) : null;
                EnergyHandler energy = access == null ? null : access.getCapability(Capabilities.Energy.ITEM);
                var afterDebit = expected.copy();
                if (quote.provider.powered) {
                    if (energy == null || energy.getAmountAsInt() < quote.baseCost) { return false; }
                    afterDebit.set(binding.energy, energy.getAmountAsInt() - bill);
                }
                action = new Action(quote, bill, particles, List.copyOf(next), targets, access, energy, afterDebit);
            } catch (Throwable failure) { disable(binding, failure); return false; }
            if (api != binding || !equipped(player, root) || !ItemStack.matches(root, expected)) { return false; }
            // Native groundstomp returns false even after success. It is not a commit receipt:
            // reuse its read-only queries, but pay this root and mutate the world here exactly once.
            // No failure catch crosses extraction/commit, break callbacks or world effects.
            if (action.quote.provider.powered && action.bill > 0) {
                try (var transaction = Transaction.openRoot()) {
                    if (action.energy.extract(action.bill, transaction) != action.bill || !equipped(player, root)
                            // Stack access stages its resource; the actual root changes on root commit.
                            || !ItemStack.matches(root, expected)
                            || !ItemStack.matches(action.access.getResource().toStack(), action.afterDebit)) { return false; }
                    transaction.commit();
                }
            }
            root.set(binding.timers, action.timers);
            if (!action.quote.provider.powered && action.bill > 0) { root.hurtAndBreak(action.bill, player, EquipmentSlot.FEET); }
            // The shared dispatcher carries an expected state through both actions. Publish only
            // our own debit/timer/wear fields; copying live callbacks would authorize unrelated edits.
            if (action.quote.provider.powered && action.bill > 0) { expected.set(binding.energy, action.afterDebit.get(binding.energy)); }
            expected.set(binding.timers, action.timers);
            if (!action.quote.provider.powered) {
                expected.setDamageValue(root.getDamageValue()); expected.setCount(root.getCount());
            }
            for (Mob mob : action.targets) {
                double x = mob.getX() - player.getX(), z = mob.getZ() - player.getZ();
                double length = Math.sqrt(x * x + z * z);
                if (length != 0) { mob.knockback(action.quote.strength, -x / length, -z / length); }
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, .5f, 1);
            if (action.particles == 0) {
                player.level().sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY(), player.getZ(), 20, .5, .2, .5, 0);
            }
            return true;
        } finally { executing.remove(player); }
    }

    static boolean equipped(ServerPlayer player, ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && player.getItemBySlot(EquipmentSlot.FEET) == root && ModContent.getMixedBootsData(root).isPresent();
    }

    // Match the native Entity-typed filter even though its world query selects Mob instances.
    private static boolean validTarget(Entity entity) { return !entity.isMultipartEntity() && !(entity instanceof PartEntity<?>); }

    static void tickEquipped(ServerPlayer player) {
        ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
        Api binding = api;
        if (binding == null || !equipped(player, root)) { return; }
        List<Object> next = new ArrayList<>();
        boolean changed = false;
        try {
            for (Object record : root.getOrDefault(binding.timers, List.of())) {
                if (!binding.groundStomp(record)) { next.add(record); continue; }
                int ticks = (int) binding.ticks.invokeExact(record);
                if ((boolean) binding.active.invokeExact(record) || ticks < 0) {
                    invalidTimer(); next.add(record); continue;
                }
                changed = true;
                if (ticks > 1) { next.add(binding.create.invokeExact("groundstomp", ticks - 1, false)); }
            }
        } catch (Throwable failure) { disable(binding, failure); return; }
        if (changed && api == binding && equipped(player, root)) { root.set(binding.timers, List.copyOf(next)); }
    }

    private record Action(Quote quote, int bill, int particles, List<Object> timers, List<Mob> targets,
                          ItemAccess access, EnergyHandler energy, ItemStack afterDebit) {}

    public enum Provider {
        ECLIPSE("eclipsealloy", true), CELESTIGEM("celestigem", true), BLAZEGOLD("blazegold", false);
        private final String path;
        private final boolean powered;
        Provider(String path, boolean powered) { this.path = path; this.powered = powered; }
        public boolean powered() { return powered; }
    }

    public record Quote(Provider provider, int strength, int baseCost, int cooldownTicks) {
        boolean preferredTo(Quote other) {
            return other == null || strength > other.strength
                    || strength == other.strength && provider.ordinal() < other.provider.ordinal();
        }
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String field, Class<?> type)
            throws ReflectiveOperationException {
        return lookup.findGetter(owner, field, type).asType(MethodType.methodType(type, Object.class));
    }

    private static <T> DataComponentType<T> scalar(String path, T example) {
        DataComponentType<T> type = component(path);
        var json = example instanceof Boolean value ? new JsonPrimitive(value) : new JsonPrimitive((Integer) example);
        if (type.codec() == null || !example.getClass().isInstance(type.codec().parse(JsonOps.INSTANCE, json).getOrThrow())) {
            throw new IllegalStateException("Unsupported Ground Stomp component: " + path);
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path)).orElseThrow().value();
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("justdirethings", path); }

    private static synchronized void invalidTimer() {
        if (!invalidTimerWarned) {
            invalidTimerWarned = true;
            Constants.LOG.warn("Just Dire Things Ground Stomp retained an unsupported cooldown; activation denied");
        }
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        synchronized (JustDireGroundStompCompatibility.class) {
            if (api == binding) {
                api = null;
                Constants.LOG.warn("Just Dire Things Ground Stomp queries failed; active compatibility disabled", failure);
            }
        }
    }

    private record NativeProvider(Provider kind, Item item) {}

    private record Api(Object ability, List<NativeProvider> providers, MethodHandle allAbilities,
                       MethodHandle usable, MethodHandle slider, MethodHandle params, MethodHandle minimum,
                       MethodHandle maximum, MethodHandle defaultValue, MethodHandle cooldown, MethodHandle feCost,
                       MethodHandle durabilityCost, MethodHandle name, MethodHandle ticks, MethodHandle active,
                       MethodHandle create, DataComponentType<List<Object>> timers, DataComponentType<Boolean> installed,
                       DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle, DataComponentType<Integer> value,
                       DataComponentType<Integer> energy) {
        NativeProvider provider(ItemStack source) {
            return providers.stream().filter(provider -> provider.item == source.getItem()).findFirst().orElse(null);
        }
        boolean permanent(ItemStack source) throws Throwable {
            return ((Set<?>) allAbilities.invokeExact(source.getItem())).contains(ability);
        }
        boolean groundStomp(Object record) throws Throwable { return "groundstomp".equals((String) name.invokeExact(record)); }
        Object copy(Object record) throws Throwable {
            return create.invokeExact((String) name.invokeExact(record), (int) ticks.invokeExact(record), (boolean) active.invokeExact(record));
        }
    }
}
