package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireCooldownHud;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Production native HUD, automatic completion boundaries and isolated guard/fault acceptance. */
final class NeoForgeJustDireCooldownHudTest {
    private static final String OUTPUT = "just-dire-cooldown-hud";
    private static final String VARIANT = JustDireCooldownFeedbackTestSupport.VARIANT;
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final StringBuilder report = new StringBuilder();
    private static Future<?> job;
    private static boolean initialized, started, finished;
    private static int waiting;
    private static ItemStack expected;
    private static Object history;
    private static int expectedCharge;
    private static double soundX, soundY, soundZ;

    static void tick(Minecraft client) {
        if (finished || client.player == null || client.level == null || client.screen != null
                || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
        try {
            if (!initialized) { initialize(client); initialized = true; }
            if (job != null) { if (!job.isDone()) { return; } job.get(); job = null; }
            Step step = steps.peek();
            if (step == null) {
                // As in the native-settings fixture, drain final ordinary slot/close packets
                // before stopping the local channel; a teardown race is not HUD acceptance.
                if (++waiting <= 20) { return; }
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report + "production HUD and completion sounds; observation only, not physical/audible acceptance\n");
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", VARIANT + "\n"); finished = true;
            } else if (!started) { step.start.run(); started = true; waiting = 0; }
            else if (step.done.getAsBoolean()) { report.append(step.name).append('\n'); steps.remove(); started = false; waiting = 0; }
            else if (++waiting > 200) { throw new AssertionError("Timed out: " + step.name + "; sounds=" + JustDireCooldownFeedbackTestSupport.sounds); }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, new AssertionError("Step: " + (steps.peek() == null ? "initialize" : steps.peek().name), failure)); finished = true; }
        if (finished) { client.stop(); }
    }

    private static void initialize(Minecraft client) throws ReflectiveOperationException {
        require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client was not muted");
        client.options.pauseOnLostFocus = false;
        if (VARIANT.equals("absent")) {
            add("quiet absence: no HUD or optional settings linkage", () -> {
                require(!JustDireSettingsCompatibility.available(), "Absent native routes available");
                var ordinary = new ItemStack(Items.DIAMOND_BOOTS);
                require(JustDireCooldownHud.view(ordinary, EquipmentSlot.FEET) == ordinary, "Absent view replaced ordinary boots");
            });
            return;
        }
        JustDireCooldownFeedbackTestSupport.bind(); JustDireEarthquakeTestSupport.bind();
        var type = Class.forName("com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay");
        add("native HUD callbacks publish readiness after real final transforms", () -> {
            boolean ready;
            try {
                var getter = type.getDeclaredMethod("mismatchedboots$justDireCooldownReady"); getter.setAccessible(true);
                ready = (boolean) getter.invoke(null);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            require(ready == !VARIANT.equals("hooksMissing"), "Wrong final hook readiness");
            require(JustDireSettingsCompatibility.available(), "HUD failure disabled existing settings");
        });
        add("actual native icon extraction and mixed read-view matrix", () -> {
            try { hud(client); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        packetView(client);
        for (int orientation = -1; orientation < 2; orientation++) {
            for (boolean active : new boolean[]{true, false}) { soundCase(client, orientation, active, false); }
        }
        soundCase(client, 0, true, true);
        if (VARIANT.equals("live")) { malformedClocks(client); soundFailure(client); }
        add("stop observing without altering persisted gameplay state", () -> JustDireCooldownFeedbackTestSupport.capturingSounds = false);
    }

    private static void hud(Minecraft client) throws ReflectiveOperationException {
        var saved = new ArrayList<ItemStack>();
        for (var slot : EquipmentSlot.values()) { saved.add(client.player.getItemBySlot(slot)); client.player.setItemSlot(slot, ItemStack.EMPTY); }
        boolean hidden = client.options.hideGui;
        try {
            nativeEquipment(client);
            for (boolean phase : new boolean[]{true, false}) {
                var nativeRoot = JustDireEarthquakeTestSupport.original(0);
                setClocks(nativeRoot, List.of(clock("groundstomp", 100, false), clock("earthquake", phase ? 100 : 400, phase)));
                client.player.setItemSlot(EquipmentSlot.FEET, nativeRoot);
                var control = extract(client);
                require(control.size() == 22, "Native eleven-ability draw control missing: " + control.size()
                        + "; textures=" + control.stream().map(draw -> draw.texture().toString()).distinct().toList()
                        + "; slots=" + java.util.Arrays.stream(EquipmentSlot.values()).map(slot -> slot + ":" + client.player.getItemBySlot(slot)).toList());
                var expectedDraws = control;
                if (VARIANT.equals("parametersMissing")) {
                    var full = nativeRoot.get(JustDireEarthquakeTestSupport.timers());
                    setClocks(nativeRoot, List.of(clock("groundstomp", 100, false)));
                    expectedDraws = extract(client); setClocks(nativeRoot, full);
                    require(expectedDraws.size() == 20, "Native GS-only partial-failure control missing");
                }
                var pair = MixedBootsFactory.createPair(JustDireEarthquakeTestSupport.original(0), new ItemStack(Items.DIAMOND_BOOTS));
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    setClocks(root, nativeRoot.get(JustDireEarthquakeTestSupport.timers()));
                    JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", false);
                    JustDireAttributeTestSupport.set(root, "tool_enabled", false);
                    var before = root.copy(); client.player.setItemSlot(EquipmentSlot.FEET, root);
                    var actual = extract(client);
                    boolean disabled = VARIANT.equals("hooksMissing");
                    require(disabled ? actual.size() == 18 : actual.equals(expectedDraws), "Native order/geometry/color mismatch: " + VARIANT);
                    require(ItemStack.matches(root, before) && client.player.getItemBySlot(EquipmentSlot.FEET) == root, "Rendering mutated/swapped actual root");
                    report.append("eleven native cooldown icons; both orientations; EQ phase=").append(phase).append("; root off/uninstalled/zero FE; exact draws or isolated fallback\n");
                }
            }
            if (!VARIANT.equals("hooksMissing") && !VARIANT.equals("parametersMissing")) {
                for (var slot : EquipmentSlot.values()) { client.player.setItemSlot(slot, ItemStack.EMPTY); }
                var root = JustDireEarthquakeTestSupport.mixed(0, false, false); client.player.setItemSlot(EquipmentSlot.FEET, root);
                setClocks(root, List.of(clock("earthquake", Integer.MAX_VALUE, true), clock("groundstomp", Integer.MAX_VALUE, false), clock("foreign", 10, false)));
                var before = root.copy(); var large = extract(client);
                require(large.size() == 4 && large.stream().allMatch(draw -> draw.height() >= 0 && draw.height() <= 9), "Large counters overflowed native extraction");
                require(ItemStack.matches(before, root), "Display clamp edited stored large/foreign records");
                setClocks(root, List.of(clock("earthquake", -1, true), clock("groundstomp", 0, false)));
                require(extract(client).isEmpty(), "Unsupported counters advertised readiness");
                setClocks(root, List.of(clock("earthquake", 100, true), clock("earthquake", 90, true), clock("groundstomp", 20, true)));
                require(extract(client).isEmpty(), "Ambiguous/unsupported phases reached native renderer");
                setClocks(root, List.of(clock("groundstomp", 100, false)));
                var one = extract(client); require(one.size() == 2, "GS-only view missing");
                client.options.hideGui = true; require(extract(client).isEmpty(), "F1 still drew mixed HUD"); client.options.hideGui = false;
                var oldPlayer = client.player; client.player = null;
                try { require(extract(client).isEmpty(), "No-player render unsafe"); } finally { client.player = oldPlayer; }
                var config = Class.forName("com.direwolf20.justdirethings.setup.Config");
                var offsetX = config.getField("OVERLAY_X").get(null); var offsetY = config.getField("OVERLAY_Y").get(null);
                var get = offsetX.getClass().getMethod("get"); var set = offsetX.getClass().getMethod("set", Object.class);
                Object oldX = get.invoke(offsetX), oldY = get.invoke(offsetY);
                try {
                    set.invoke(offsetX, (int) oldX + 17); set.invoke(offsetY, (int) oldY + 13);
                    var shifted = extract(client);
                    require(shifted.getFirst().x() == one.getFirst().x() - 17 && shifted.getFirst().y() == one.getFirst().y() - 13,
                            "Native client offsets not respected");
                } finally { set.invoke(offsetX, oldX); set.invoke(offsetY, oldY); }
                report.append("GS-only; safe maximum counters; retained unsupported/duplicate/foreign evidence; F1/no player; actual native config offsets\n");
                root.setCount(2); require(extract(client).isEmpty(), "Non-count-one root acquired a native HUD identity"); root.setCount(1);
                client.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(root.getItem()));
                require(extract(client).isEmpty(), "Missing mixed provenance acquired a native HUD identity");
                client.player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); client.player.setItemSlot(EquipmentSlot.OFFHAND, root);
                require(extract(client).isEmpty(), "Non-FEET mixed clock was projected");
                report.append("invalid count/provenance and non-FEET roots keep native rejection\n");
            }
        } finally {
            client.options.hideGui = hidden;
            int index = 0; for (var slot : EquipmentSlot.values()) { client.player.setItemSlot(slot, saved.get(index++)); }
        }
    }

    private static void nativeEquipment(Minecraft client) throws ReflectiveOperationException {
        equip(client, EquipmentSlot.HEAD, "helmet", List.of(clock("stupefy", 50, true), clock("debuffremover", 200, false), clock("noai", 1200, false)));
        equip(client, EquipmentSlot.CHEST, "chestplate", List.of(clock("invulnerability", 100, true), clock("extinguish", 20, false), clock("deathprotection", 3000, false)));
        equip(client, EquipmentSlot.LEGS, "leggings", List.of(clock("decoy", 100, true)));
        equip(client, EquipmentSlot.MAINHAND, "sword", List.of(clock("cauterizewounds", 600, false)));
        equip(client, EquipmentSlot.OFFHAND, "bow", List.of(clock("epicarrow", 1200, false)));
    }
    private static void equip(Minecraft client, EquipmentSlot slot, String item, List<Object> clocks) {
        String path = item.equals("bow") ? "bow_eclipsealloy" : "eclipsealloy_" + item;
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value());
        setClocks(stack, clocks); client.player.setItemSlot(slot, stack);
    }
    private static List<JustDireCooldownFeedbackTestSupport.Draw> extract(Minecraft client) throws ReflectiveOperationException {
        return JustDireCooldownFeedbackTestSupport.extract(client);
    }

    private static void packetView(Minecraft client) {
        add("seed off/uninstalled charged root for ordinary production equipment packets", () -> server(client, player -> {
            try {
                player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                expected = JustDireEarthquakeTestSupport.mixed(0, false, false);
                expected.set(JustDireResourceTestSupport.energyComponent(), 13_579);
                JustDireAttributeTestSupport.set(expected, "tool_enabled", false);
                JustDireAttributeTestSupport.set(expected, "earthquake_upgrade_installed", false);
                setClocks(expected, List.of(clock("groundstomp", 150, false), clock("earthquake", 150, true)));
                history = ModContent.getMixedBootsData(expected).orElseThrow();
                JustDireCooldownFeedbackTestSupport.sounds.clear(); JustDireCooldownFeedbackTestSupport.capturingSounds = true;
                player.setItemSlot(EquipmentSlot.FEET, expected); player.inventoryMenu.broadcastChanges();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }));
        add("actual root clock progression/FE/provenance reaches native draws without alternate ticking", () -> {}, () -> {
            try {
                var root = client.player.getItemBySlot(EquipmentSlot.FEET);
                if (!ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(expected))) { return false; }
                var clock = JustDireEarthquakeTestSupport.clock(root, "earthquake");
                if (clock == null || !clock.active() || clock.ticks() >= 150) { return false; }
                require(clock.ticks() > 0 && JustDireEarthquakeTestSupport.charge(root) == 13_579
                        && ModContent.getMixedBootsData(root).orElseThrow().equals(history), "HUD changed resources/history or spent clock");
                var before = root.copy(); var draws = extract(client);
                require(draws.size() == (VARIANT.equals("hooksMissing") ? 0 : VARIANT.equals("parametersMissing") ? 2 : 4)
                        && ItemStack.matches(before, root) && JustDireCooldownFeedbackTestSupport.sounds.isEmpty(), "Packet HUD mutation or load-only feedback");
                return true;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    private static void soundCase(Minecraft client, int orientation, boolean active, boolean pause) {
        add("seed real worn " + (orientation < 0 ? "native" : "mixed " + orientation) + " boundary active=" + active + " pause=" + pause,
                () -> server(client, player -> {
                    try {
                        player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        expected = orientation < 0 ? JustDireEarthquakeTestSupport.original(0) : JustDireEarthquakeTestSupport.mixed(0, orientation == 1, false);
                        expectedCharge = pause ? 0 : 13_579;
                        expected.set(JustDireResourceTestSupport.energyComponent(), expectedCharge);
                        expected.setDamageValue(37);
                        JustDireAttributeTestSupport.set(expected, "tool_enabled", false);
                        JustDireAttributeTestSupport.set(expected, "earthquake_upgrade_installed", false);
                        JustDireAttributeTestSupport.set(expected, "groundstomp_upgrade_installed", false);
                        JustDireAttributeTestSupport.set(expected, "groundstomp_toggle", false);
                        JustDireAttributeTestSupport.set(expected, "earthquake_toggle", false);
                        setClocks(expected, List.of(clock("groundstomp", 1, false), clock("earthquake", 1, active)));
                        history = ModContent.getMixedBootsData(expected).orElse(null);
                        soundX = player.getX(); soundY = player.getY(); soundZ = player.getZ();
                        player.setItemSlot(EquipmentSlot.FEET, pause ? ItemStack.EMPTY : expected);
                        if (pause) { player.getInventory().setItem(0, expected); }
                        JustDireCooldownFeedbackTestSupport.observedPlayer = player;
                        JustDireCooldownFeedbackTestSupport.sounds.clear(); JustDireCooldownFeedbackTestSupport.capturingSounds = true;
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                }));
        if (pause) {
            add("unequipped real inventory pauses without completion feedback", () -> {}, () -> waiting > 10);
            add("pause retained both spent clocks and emits no sound on re-equip itself", () -> server(client, player -> {
                try {
                    require(JustDireCooldownFeedbackTestSupport.sounds.isEmpty() && JustDireEarthquakeTestSupport.clock(expected, "earthquake").ticks() == 1,
                            "Unequipped root ticked or sounded");
                    player.getInventory().setItem(0, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, expected);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }));
        }
        add("automatic ordinary ticks commit the expected two completion events", () -> {},
                () -> JustDireCooldownFeedbackTestSupport.sounds.size() >= 2 && waiting > 5);
        add("world event kind/position/volume/pitch, no repetition/bill/history mutation", () -> server(client, player -> {
            require(player.getItemBySlot(EquipmentSlot.FEET) == expected, "Actual equipment replaced");
            var sounds = List.copyOf(JustDireCooldownFeedbackTestSupport.sounds);
            require(sounds.size() == 2 && sounds.stream().filter(sound -> sound.event() == SoundEvents.ENDER_EYE_DEATH).count() == (active ? 1 : 2)
                    && sounds.stream().filter(sound -> sound.event() == SoundEvents.CONDUIT_DEACTIVATE).count() == (active ? 1 : 0), "Completion event order/count differs: " + sounds);
            if (active) {
                // Native flushes removals after phase transitions; the two owned tickers commit GS then EQ.
                require(sounds.getFirst().event() == (orientation < 0 ? SoundEvents.CONDUIT_DEACTIVATE : SoundEvents.ENDER_EYE_DEATH),
                        "Coincident transition order changed");
                report.append(orientation < 0 ? "native coincidence: active-end then ready\n" : "local ticker coincidence: ready then active-end; same events, distinct scheduling\n");
            }
            for (var sound : sounds) {
                require(sound.source() == SoundSource.PLAYERS && sound.volume() == 1 && sound.pitch() == 1 && !sound.excluded()
                        && Math.abs(sound.x() - soundX) < 1e-6 && Math.abs(sound.y() - soundY) < 1e-6 && Math.abs(sound.z() - soundZ) < 1e-6, "Private/wrong-position sound");
            }
            if (orientation >= 0) {
                var first = sounds.getFirst().committedClocks(); var last = sounds.getLast().committedClocks();
                require(first.stream().noneMatch(clock -> clock.name().equals("groundstomp"))
                        && first.stream().anyMatch(clock -> clock.name().equals("earthquake") && clock.ticks() == 1 && clock.active() == active),
                        "GS completion sounded before committed expiry");
                require(active ? last.stream().anyMatch(clock -> clock.name().equals("earthquake") && clock.ticks() == 800 && !clock.active())
                        : last.stream().noneMatch(clock -> clock.name().equals("earthquake")), "EQ completion sounded before committed transition");
            }
            require(JustDireEarthquakeTestSupport.charge(expected) == expectedCharge && expected.getDamageValue() == 37
                    && ModContent.getMixedBootsData(expected).orElse(null) == history,
                    "Feedback refilled/billed/rewrote root provenance");
            try {
                require(JustDireEarthquakeTestSupport.clock(expected, "groundstomp") == null, "GS did not expire");
                var clock = JustDireEarthquakeTestSupport.clock(expected, "earthquake");
                require(active ? clock != null && !clock.active() && clock.ticks() > 0 && clock.ticks() <= 800 : clock == null, "Candidate replaced production clock semantics");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }));
        add("ordinary equipment packets expose the spent live clock to the native HUD", () -> {}, () -> {
            try {
                var root = client.player.getItemBySlot(EquipmentSlot.FEET);
                if (!ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(expected)) || root.getItem() != expected.getItem()) { return false; }
                var clock = JustDireEarthquakeTestSupport.clock(root, "earthquake");
                if (active ? clock == null || clock.active() : clock != null) { return false; }
                boolean disabled = orientation >= 0 && (VARIANT.equals("hooksMissing") || VARIANT.equals("parametersMissing"));
                var before = root.copy(); var draws = extract(client);
                require(draws.size() == (active && !disabled ? 2 : 0) && ItemStack.matches(before, root), "Packet-backed HUD mutated/misrepresented spent state");
                return true;
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    private static void malformedClocks(Minecraft client) {
        for (String scenario : List.of("zero", "negative", "duplicate", "unsupportedPhase")) {
            add("seed retained/cleared malformed clocks: " + scenario, () -> server(client, player -> {
                try {
                    expected = JustDireEarthquakeTestSupport.mixed(0, false, false);
                    expected.set(JustDireResourceTestSupport.energyComponent(), 13_579);
                    history = ModContent.getMixedBootsData(expected).orElseThrow();
                    var records = new ArrayList<Object>();
                    if (scenario.equals("duplicate")) {
                        records.add(clock("groundstomp", 1, false)); records.add(clock("groundstomp", 1, false));
                        records.add(clock("earthquake", 1, true)); records.add(clock("earthquake", 1, false));
                    } else {
                        records.add(clock("groundstomp", scenario.equals("zero") ? 0 : scenario.equals("negative") ? -1 : 1, scenario.equals("unsupportedPhase")));
                        records.add(clock("earthquake", scenario.equals("negative") ? -1 : 0, false));
                    }
                    records.add(clock("foreign", 1, false));
                    setClocks(expected, records); player.setItemSlot(EquipmentSlot.FEET, expected);
                    JustDireCooldownFeedbackTestSupport.observedPlayer = player;
                    JustDireCooldownFeedbackTestSupport.sounds.clear(); JustDireCooldownFeedbackTestSupport.capturingSounds = true;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }));
            add("ordinary malformed ticks do not invent a ready cue", () -> {}, () -> waiting > 10);
            add("preserve existing malformed handling without payment/provenance edits", () -> server(client, player -> {
                var actual = JustDireCooldownFeedbackTestSupport.before(player);
                require(JustDireCooldownFeedbackTestSupport.sounds.isEmpty() && actual.stream().anyMatch(clock -> clock.name().equals("foreign") && clock.ticks() == 1),
                        "Malformed/foreign state emitted feedback or was repaired");
                require(actual.stream().filter(clock -> clock.name().equals("groundstomp")).count()
                        == (scenario.equals("zero") || scenario.equals("duplicate") ? 0 : 1), "GS malformed handling changed");
                require(actual.stream().filter(clock -> clock.name().equals("earthquake")).count() == (scenario.equals("duplicate") ? 2 : 1),
                        "EQ malformed evidence was cleared");
                require(JustDireEarthquakeTestSupport.charge(expected) == 13_579 && ModContent.getMixedBootsData(expected).orElseThrow() == history,
                        "Malformed feedback changed resources/history");
            }));
        }
    }

    private static void soundFailure(Minecraft client) {
        add("inject world-audio failures after actual ticker commits, never return a retryable result", () -> server(client, player -> {
            try {
                JustDireCooldownFeedbackTestSupport.sounds.clear();
                for (boolean earthquake : new boolean[]{false, true}) {
                    expected = JustDireEarthquakeTestSupport.mixed(0, false, false);
                    expected.set(JustDireResourceTestSupport.energyComponent(), 13_579);
                    history = ModContent.getMixedBootsData(expected).orElseThrow();
                    setClocks(expected, List.of(clock(earthquake ? "earthquake" : "groundstomp", 1, earthquake)));
                    player.setItemSlot(EquipmentSlot.FEET, expected); JustDireCooldownFeedbackTestSupport.observedPlayer = player;
                    var owner = earthquake ? JustDireEarthquakeCompatibility.class : JustDireGroundStompCompatibility.class;
                    var tick = owner.getDeclaredMethod("tickEquipped", ServerPlayer.class); tick.setAccessible(true);
                    JustDireCooldownFeedbackTestSupport.failNextSound = true;
                    try {
                        tick.invoke(null, player); throw new AssertionError("Committed audio failure was swallowed");
                    } catch (InvocationTargetException failure) {
                        require(failure.getCause() instanceof JustDireCooldownFeedbackTestSupport.SoundProbeFailure, "Unexpected ticker failure");
                    } finally { JustDireCooldownFeedbackTestSupport.failNextSound = false; }
                    var pending = JustDireEarthquakeTestSupport.clock(expected, earthquake ? "earthquake" : "groundstomp");
                    require(earthquake ? pending != null && !pending.active() && pending.ticks() == 800 : pending == null,
                            "Audio failure rolled back/reset the committed clock");
                    require(JustDireGroundStompCompatibility.available() && JustDireEarthquakeCompatibility.available()
                            && JustDireEarthquakeTestSupport.charge(expected) == 13_579 && ModContent.getMixedBootsData(expected).orElseThrow() == history,
                            "Post-commit audio failure disabled binding or refunded resources");
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }));
        add("ordinary later ticks progress without replaying failed completion audio", () -> {}, () -> waiting > 10);
        add("two committed failures remain two attempts, not another activation/payment", () -> server(client, player ->
                require(JustDireCooldownFeedbackTestSupport.sounds.size() == 2, "Audio failure was replayed on later ticks")));
    }

    private static Object clock(String name, int ticks, boolean active) throws ReflectiveOperationException { return JustDireCooldownFeedbackTestSupport.record(name, ticks, active); }
    private static void setClocks(ItemStack root, List<Object> records) { root.set(JustDireEarthquakeTestSupport.timers(), records); }
    private static void server(Minecraft client, Consumer<ServerPlayer> action) {
        var id = client.player.getUUID(); var server = client.getSingleplayerServer();
        job = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }
    private static void add(String name, Runnable action) { add(name, action, () -> true); }
    private static void add(String name, Runnable action, BooleanSupplier done) { steps.add(new Step(name, action, done)); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private record Step(String name, Runnable start, BooleanSupplier done) {}
}
