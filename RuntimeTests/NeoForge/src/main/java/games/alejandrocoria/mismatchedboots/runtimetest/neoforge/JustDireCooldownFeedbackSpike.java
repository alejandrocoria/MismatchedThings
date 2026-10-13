package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Opt-in research only: a native HUD read view and feedback around production ticking, never another clock. */
public final class JustDireCooldownFeedbackSpike {
    public static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireCooldownFeedbackVariant", "live");
    public static volatile boolean hudReady, capturingDraws, capturingSounds;
    public static final List<Draw> draws = new ArrayList<>();
    public static final ConcurrentLinkedQueue<Sound> sounds = new ConcurrentLinkedQueue<>();
    private static Method name, ticks, active, parameters;
    private static Constructor<?> timer;
    private static Object stomp, earthquake;
    private static ItemStack nativeBoots;
    private static boolean parameterWarning;

    private JustDireCooldownFeedbackSpike() {}

    static synchronized void bind() throws ReflectiveOperationException {
        if (timer != null) { return; }
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
        name = record.getMethod("abilityName"); ticks = record.getMethod("cooldownTicks"); active = record.getMethod("isactive");
        parameters = tool.getMethod("getAbilityParams", ability);
        stomp = ability.getField("GROUNDSTOMP").get(null); earthquake = ability.getField("EARTHQUAKE").get(null);
        nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots")));
        timer = record.getConstructor(String.class, int.class, boolean.class);
    }

    static Object record(String ability, int remaining, boolean phase) throws ReflectiveOperationException {
        bind(); return timer.newInstance(ability, remaining, phase);
    }

    public static ItemStack unavailableView(Player player, EquipmentSlot slot, ItemStack original) { return original; }

    /** Only this renderer sees the detached item; no actual equipment or resource/source component is replaced. */
    public static ItemStack view(Player player, EquipmentSlot slot, ItemStack root) {
        if (!hudReady || slot != EquipmentSlot.FEET || !mixed(root)) { return root; }
        try {
            bind();
            if (VARIANT.equals("parametersMissing")) {
                if (!parameterWarning) { parameterWarning = true; System.out.println("JDT feedback spike: HUD parameters unavailable; mixed HUD only disabled"); }
                return root;
            }
            var records = root.getOrDefault(JustDireEarthquakeTestSupport.timers(), List.of());
            var shown = new ArrayList<Object>();
            for (Object value : records) {
                var clock = read(value); String ability = clock.name;
                if (!List.of("groundstomp", "earthquake").contains(ability)
                        || records.stream().filter(candidate -> named(candidate, ability)).count() != 1) { continue; }
                if (clock.ticks <= 0 || ability.equals("groundstomp") && clock.active) { continue; }
                Object params = parameters.invoke(nativeBoots.getItem(), ability.equals("groundstomp") ? stomp : earthquake);
                int duration = params.getClass().getField(clock.active ? "activeCooldown" : "cooldown").getInt(params);
                // The unmodified native renderer multiplies ints by nine: normalize only its detached display counter.
                if (duration <= 0 || duration > Integer.MAX_VALUE / 9) { continue; }
                shown.add(record(ability, Math.min(clock.ticks, duration), clock.active));
            }
            // Iteration preserves the root's record order and the native shared index with other equipment.
            var view = nativeBoots.copy(); view.set(JustDireEarthquakeTestSupport.timers(), List.copyOf(shown));
            return view;
        } catch (ReflectiveOperationException failure) { throw new AssertionError("HUD research binding failed", failure); }
    }

    public static List<Clock> before(ServerPlayer player) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        if (!mixed(root)) { return List.of(); }
        try { bind(); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        return root.getOrDefault(JustDireEarthquakeTestSupport.timers(), List.of()).stream().map(JustDireCooldownFeedbackSpike::read).toList();
    }

    /** Observe only a committed supported transition in an existing ticker; do not change any timer. */
    public static void completed(ServerPlayer player, ItemStack root, List<Clock> before) {
        if (!mixed(root) || player.getItemBySlot(EquipmentSlot.FEET) != root) { return; }
        var after = before(player);
        for (String ability : List.of("groundstomp", "earthquake")) {
            var prior = before.stream().filter(clock -> clock.name.equals(ability)).toList();
            if (prior.size() != 1 || prior.getFirst().ticks != 1 || ability.equals("groundstomp") && prior.getFirst().active) { continue; }
            var next = after.stream().filter(clock -> clock.name.equals(ability)).toList();
            SoundEvent event = null;
            if (!prior.getFirst().active && next.isEmpty()) { event = SoundEvents.ENDER_EYE_DEATH; }
            else if (prior.getFirst().active && next.size() == 1 && !next.getFirst().active && next.getFirst().ticks == 800) {
                event = SoundEvents.CONDUIT_DEACTIVATE;
            }
            if (event != null) { player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, 1, 1); }
        }
    }

    private static boolean mixed(ItemStack root) {
        return root.getCount() == 1 && root.getItem() instanceof MixedBootsItem && ModContent.getMixedBootsData(root).isPresent();
    }
    private static boolean named(Object record, String ability) { return read(record).name.equals(ability); }
    private static Clock read(Object record) {
        try { return new Clock((String) name.invoke(record), (int) ticks.invoke(record), (boolean) active.invoke(record)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public record Clock(String name, int ticks, boolean active) {}
    public record Draw(Identifier texture, int x, int y, float u, float v, int width, int height,
                       int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {}
    public record Sound(SoundEvent event, SoundSource source, double x, double y, double z, float volume, float pitch, boolean excluded) {}
}
