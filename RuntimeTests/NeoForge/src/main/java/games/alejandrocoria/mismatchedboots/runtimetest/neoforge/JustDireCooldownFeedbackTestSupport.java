package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Test-only native draw/world-event observation; no alternate view, ticking or sound emission. */
public final class JustDireCooldownFeedbackTestSupport {
    public static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireCooldownHudVariant", "live");
    public static volatile boolean capturingDraws;
    public static volatile boolean capturingSounds;
    public static ServerPlayer observedPlayer;
    public static boolean failNextSound;

    public static final class SoundProbeFailure extends RuntimeException {
    }

    public static final List<Draw> draws = new ArrayList<>();
    public static final ConcurrentLinkedQueue<Sound> sounds = new ConcurrentLinkedQueue<>();
    private static Method name;
    private static Method ticks;
    private static Method active;
    private static Constructor<?> timer;

    private JustDireCooldownFeedbackTestSupport() {
    }

    static synchronized void bind() throws ReflectiveOperationException {
        if (timer != null) {
            return;
        }
        var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
        name = record.getMethod("abilityName");
        ticks = record.getMethod("cooldownTicks");
        active = record.getMethod("isactive");
        timer = record.getConstructor(String.class, int.class, boolean.class);
    }

    static Object record(String ability, int remaining, boolean phase) throws ReflectiveOperationException {
        bind();
        return timer.newInstance(ability, remaining, phase);
    }

    public static List<Clock> before(ServerPlayer player) {
        try {
            bind();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        return player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireEarthquakeTestSupport.timers(), List.of())
                     .stream().map(JustDireCooldownFeedbackTestSupport::read).toList();
    }

    private static Clock read(Object record) {
        try {
            return new Clock((String) name.invoke(record), (int) ticks.invoke(record), (boolean) active.invoke(record));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    static void begin(ServerPlayer player) {
        observedPlayer = player;
        sounds.clear();
        capturingSounds = true;
    }

    static void assertCompletions(int ready, int activeEnd) {
        long actualReady = sounds.stream().filter(sound -> sound.event == SoundEvents.ENDER_EYE_DEATH).count();
        long actualEnd = sounds.stream().filter(sound -> sound.event == SoundEvents.CONDUIT_DEACTIVATE).count();
        require(actualReady == ready && actualEnd == activeEnd,
                "Completion counts differ: ready=" + actualReady + ", active-end=" + actualEnd + "; events=" + sounds);
    }

    /** Compare actual mixed extraction to the native control using the same packet-delivered counters. */
    static void assertHud(Minecraft client, int icons) {
        var root = client.player.getItemBySlot(EquipmentSlot.FEET);
        var before = root.copy();
        try {
            var actual = extract(client);
            var nativeControl = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings",
                                                                                                              "eclipsealloy_boots")));
            nativeControl.set(JustDireEarthquakeTestSupport.timers(), root.getOrDefault(JustDireEarthquakeTestSupport.timers(), List.of()));
            client.player.setItemSlot(EquipmentSlot.FEET, nativeControl);
            var control = extract(client);
            require(actual.size() == icons * 2 && actual.equals(control), "Lifecycle native HUD differs: " + actual + " vs " + control);
            require(ItemStack.matches(before, root), "Lifecycle HUD mutated the packet-delivered root");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        } finally {
            client.player.setItemSlot(EquipmentSlot.FEET, root);
        }
    }

    static List<Draw> extract(Minecraft client) throws ReflectiveOperationException {
        var overlay = Class.forName("com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay");
        var render = overlay.getMethod("render", GuiGraphicsExtractor.class, net.minecraft.client.DeltaTracker.class);
        draws.clear();
        capturingDraws = true;
        try {
            render.invoke(overlay.getField("INSTANCE").get(null),
                          new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0),
                          client.getDeltaTracker());
        } finally {
            capturingDraws = false;
        }
        return List.copyOf(draws);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public record Clock(String name, int ticks, boolean active) {
    }

    public record Draw(Identifier texture, int x, int y, float u, float v, int width, int height,
                       int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {
    }

    public record Sound(SoundEvent event, SoundSource source, double x, double y, double z, float volume, float pitch,
                        boolean excluded, List<Clock> committedClocks) {
    }
}
