package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** One real integrated connection; server-owned stacks stay on the server, only JSON crosses threads. */
final class SilentGearRemainingClientTest {
    private static final String OUTPUT = "silent-gear-remaining";
    private static final String VOID_VARIANT = System.getProperty("mismatchedboots.runtimetest.silentGearVoidVariant", "");
    private static final boolean VOID_REFUSED = VOID_VARIANT.equals("refused");
    private static final BlockPos SNOW = new BlockPos(8, 80, 8);
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks, mode, index;
    private static boolean finished, initialized, released, reloaded;
    private static Object oldPoisonEffect;
    private static Future<String> job;
    private static JsonObject state;
    private static Session session; // Integrated-server jobs only.
    private static List<ItemStack> persisted; // Server jobs only; capture before other scenes clear inventory.
    private static int lifecycleIndex;
    private static boolean lifecycleFinishing;
    private static String reportOutput = OUTPUT;

    private SilentGearRemainingClientTest() {}

    static void captureRejoin(ServerPlayer owner) throws Exception {
        var saved = JsonParser.parseString(java.nio.file.Files.readString(manifest())).getAsJsonObject();
        require(saved.get("process").getAsLong() != ProcessHandle.current().pid()
                && saved.get("uuid").getAsString().equals(owner.getUUID().toString()), "Remaining-effects rejoin reused JVM/changed UUID");
        persisted = List.of(owner.getInventory().getItem(13), owner.getInventory().getItem(14), owner.getInventory().getItem(15));
        var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < 3; i++) {
            var expected = ItemStack.CODEC.parse(ops, saved.getAsJsonArray("roots").get(i)).getOrThrow();
            require(ItemStack.matches(expected, persisted.get(i)) && persisted.get(i).getDamageValue() == 3,
                    "Player rejoin changed used remaining-effect root/source/history " + i);
        }
    }

    static void restore(ServerPlayer owner) {
        for (int i = 0; i < 3; i++) { owner.getInventory().setItem(13 + i, persisted.get(i)); }
    }

    private static java.nio.file.Path manifest() {
        return java.nio.file.Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "silent-gear-remaining-player.json");
    }

    /** Only three used-root scenes, sharing the normal gate's observation and Session code. */
    static boolean advanceLifecycle(Minecraft client, boolean rejoin) throws Exception {
        if (finished) { return true; }
        reportOutput = "silent-gear-remaining-" + (rejoin ? "rejoin" : "client");
        if (job != null) {
            if (!job.isDone()) { return false; }
            state = JsonParser.parseString(job.get()).getAsJsonObject(); job = null;
            if (lifecycleFinishing) { finished = true; return true; }
        }
        var server = client.getSingleplayerServer(); var uuid = client.player.getUUID();
        if (state == null || !state.has("root")) {
            mode = lifecycleIndex; index = mode == 0 ? 1 : mode == 1 ? 6 : 1;
            int nextMode = mode, nextSample = index;
            job = server.submit(() -> {
                try {
                    var owner = server.getPlayerList().getPlayer(uuid);
                    if (persisted == null) {
                        var barrel = SilentGearLifecycleTest.barrel(server);
                        persisted = List.of(barrel.getItem(9).copy(), barrel.getItem(10).copy(), barrel.getItem(11).copy());
                    }
                    session = new Session(owner, nextMode, nextSample, persisted.get(nextMode)); return session.poll();
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }); return false;
        }
        if (!observed(client)) { return false; }
        double clientY = client.player.getY(); boolean last = ++lifecycleIndex == 3;
        job = server.submit(() -> {
            session.finish(clientY);
            if (last) {
                var owner = server.getPlayerList().getPlayer(uuid); restore(owner);
                if (!rejoin) {
                    var saved = new JsonObject(); var roots = new com.google.gson.JsonArray();
                    var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    for (var root : persisted) { roots.add(ItemStack.CODEC.encodeStart(ops, root).getOrThrow()); }
                    saved.addProperty("process", ProcessHandle.current().pid()); saved.addProperty("uuid", uuid.toString()); saved.add("roots", roots);
                    NetworkAcceptanceFiles.write("silent-gear-remaining-player.json", saved.toString());
                }
            }
            return "{}";
        });
        lifecycleFinishing = last; released = false; return false;
    }

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Remaining-effects client timeout mode=" + mode + " index=" + index + " state=" + state);
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            client.options.pauseOnLostFocus = false;
            if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) { client.setScreen(null); }
            if (job != null) {
                if (!job.isDone()) { return; }
                if (mode == 4) { complete(client); return; }
                state = JsonParser.parseString(job.get()).getAsJsonObject(); job = null;
            }
            var server = client.getSingleplayerServer(); var uuid = client.player.getUUID();
            if (!initialized) {
                initialized = true;
                job = server.submit(() -> {
                    try { controls(server.getPlayerList().getPlayer(uuid)); return "{}"; }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }); return;
            }
            if (!reloaded && VOID_VARIANT.isEmpty()) {
                reloaded = true;
                job = server.submit(() -> server.reloadResources(server.getPackRepository().getSelectedIds()))
                        .thenCompose(reload -> reload).thenApplyAsync(ignored -> {
                            try { reloadControl(server.getPlayerList().getPlayer(uuid)); return "{}"; }
                            catch (Exception failure) { throw new IllegalStateException(failure); }
                        }, server);
                return;
            }
            if (state == null || !state.has("root")) {
                int nextMode = mode, nextIndex = index;
                job = server.submit(() -> {
                    try { session = new Session(server.getPlayerList().getPlayer(uuid), nextMode, nextIndex); return session.poll(); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }); return;
            }
            if (!observed(client)) { return; }
            int doneMode = mode; double clientY = client.player.getY();
            job = server.submit(() -> session.finish(clientY));
            if (VOID_REFUSED) { mode = 4; return; }
            if (++index == (doneMode == 0 ? 3 : doneMode == 1 ? 11 : 5)) { mode++; index = 0; }
            if (!VOID_VARIANT.isEmpty() && mode == 1) { mode = 4; }
            released = false;
            if (mode == 3) {
                // Finish the final server assertion before declaring success/stopping the client.
                mode = 4;
            }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop(); }
    }

    /** Shared natural movement/collision/effect-packet observation for normal and used-root lifecycle scenes. */
    private static boolean observed(Minecraft client) {
        var server = client.getSingleplayerServer();
        ItemStack expected = ItemStack.CODEC.parse(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), state.get("root")).getOrThrow();
        ItemStack received = mode == 2 ? held(client.player, index) : client.player.getItemBySlot(EquipmentSlot.FEET);
        if (!ItemStack.matches(expected, received)) { job = server.submit(() -> session.poll()); return false; }
        if (mode == 0) {
            if (!state.get("rescued").getAsBoolean() || !client.player.hasEffect(MobEffects.LEVITATION)
                    || !client.player.hasEffect(MobEffects.SLOW_FALLING) || client.player.getY() < state.get("startY").getAsDouble() + 1
                    || state.get("y").getAsDouble() < state.get("startY").getAsDouble() + 1) {
                job = server.submit(() -> session.poll()); return false;
            }
        } else if (mode == 1) {
            if (!released) {
                if (Math.abs(client.player.getY() - 82) > .1) { return false; }
                released = true; job = server.submit(() -> session.release()); return false;
            }
            if (state.get("age").getAsInt() < 45) { job = server.submit(() -> session.poll()); return false; }
            boolean supported = index == 0 || index >= 5;
            require((client.player.getY() >= 80.95) == supported && (state.get("y").getAsDouble() >= 80.95) == supported,
                    "Powder-snow real collision differs index=" + index + " server=" + state + " clientY=" + client.player.getY());
            require(received.canWalkOnPowderedSnow(client.player) == supported, "Client snow callback differs");
            if (supported) { descending(client.player); }
        } else {
            if (state.get("age").getAsInt() < 10) { job = server.submit(() -> session.poll()); return false; }
            boolean poison = index == 4, wither = index >= 3;
            require(state.get("poison").getAsBoolean() == poison && state.get("wither").getAsBoolean() == wither
                    && state.get("night").getAsBoolean(), "Ordinary cure server ticks differ: " + state);
            if (client.player.hasEffect(MobEffects.POISON) != poison || client.player.hasEffect(MobEffects.WITHER) != wither
                    || !client.player.hasEffect(MobEffects.NIGHT_VISION)) { job = server.submit(() -> session.poll()); return false; }
        }
        return true;
    }

    private static void complete(Minecraft client) {
        try {
            job.get(); job = null;
            NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "native controls and production mixed remaining effects\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, failure); }
        finished = true; client.stop();
    }

    private static ItemStack held(Player player, int sample) {
        return sample == 4 ? player.getInventory().getItem(7)
                : player.getItemBySlot(sample == 2 ? EquipmentSlot.MAINHAND : sample == 3 ? EquipmentSlot.OFFHAND : EquipmentSlot.FEET);
    }

    private static void clean(ServerPlayer owner) {
        owner.getInventory().clearContent(); owner.getInventory().setSelectedSlot(0); owner.removeAllEffects();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) { owner.setItemSlot(slot, ItemStack.EMPTY); }
        owner.setGameMode(GameType.SURVIVAL); owner.setHealth(owner.getMaxHealth()); owner.getFoodData().setFoodLevel(20);
        owner.setNoGravity(true); owner.setDeltaMovement(Vec3.ZERO); owner.fallDistance = 0; owner.setShiftKeyDown(false);
        SilentGearBounceTest.protectedRoot = null;
        SilentGearRemainingFixture.queries = SilentGearRemainingFixture.pushes = SilentGearRemainingFixture.cureTicks = 0;
        SilentGearRemainingFixture.cureCalls.clear();
    }

    private static void effects(net.minecraft.world.entity.LivingEntity player) {
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 400));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400));
    }

    private static void descending(Player player) {
        var state = Blocks.POWDER_SNOW.defaultBlockState();
        require(!state.getCollisionShape(player.level(), SNOW, CollisionContext.of(player)).isEmpty(), "Supported snow collision missing");
        Input oldInput = player instanceof net.minecraft.client.player.LocalPlayer local ? local.input.keyPresses : null;
        if (oldInput != null) {
            ((net.minecraft.client.player.LocalPlayer) player).input.keyPresses = new Input(oldInput.forward(), oldInput.backward(), oldInput.left(), oldInput.right(), oldInput.jump(), true, oldInput.sprint());
        }
        player.setShiftKeyDown(true);
        try {
            require(player.isDescending(), "Fixture did not select native descending input");
            require(state.getCollisionShape(player.level(), SNOW, CollisionContext.of(player)).isEmpty(), "Snow walking blocked native descending");
        } finally {
            player.setShiftKeyDown(false);
            if (oldInput != null) { ((net.minecraft.client.player.LocalPlayer) player).input.keyPresses = oldInput; }
        }
    }

    private static void controls(ServerPlayer owner) throws Exception {
        var api = new NeoForgeSilentGearTest.NativeApi(); SilentGearTraitSpike.initialize(api);
        var writer = SilentGearTraitSpike.nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class);
        ItemStack witness = api.boot("iron"); SilentGearBounceTest.protectedRoot = witness;
        boolean calibrated = false;
        try { writer.invoke(null, witness, owner); }
        catch (InvocationTargetException failure) { calibrated = failure.getCause() instanceof AssertionError; }
        finally { SilentGearBounceTest.protectedRoot = null; }
        require(calibrated, "Remaining-effects native recalculation tripwire is inactive");
        ItemStack nativeVoid = api.boot("tyrian_steel"); require(api.traitLevel(nativeVoid, "void_ward") == 1, "Stock Tyrian boot lacks Void Ward");
        var subjects = new ArrayList<ItemStack>(); subjects.add(nativeVoid);
        subjects.addAll(NeoForgeSilentGearTest.workbench(owner, nativeVoid, new ItemStack(Items.DIAMOND_BOOTS)));
        subjects.add(new ItemStack(Items.DIAMOND_BOOTS));
        var handler = SilentGearTraitSpike.nativeClass("event.GearEvents").getMethod("onPlayerTick", PlayerTickEvent.Pre.class);
        int controls = 0;
        var ready = SilentGearTraitSpike.nativeClass("event.GearEvents").getDeclaredMethod("mismatchedboots$sgVoidReady"); ready.setAccessible(true);
        require((boolean) ready.invoke(null) != VOID_REFUSED, "Final production Void Ward proof differs");
        for (int sample = 0; sample < subjects.size(); sample++) {
                for (boolean below : List.of(false, true)) {
                    clean(owner);
                    ItemStack root = subjects.get(sample).copy(), before = root.copy(); owner.setItemSlot(EquipmentSlot.FEET, root);
                    owner.setPos(8.5, owner.level().getMinY() - 32 - (below ? .5 : 0), 8.5);
                    if (ModContent.getMixedBootsData(root).isPresent()) { SilentGearBounceTest.protectedRoot = root; }
                    handler.invoke(null, new PlayerTickEvent.Pre(owner));
                    boolean expected = below && (sample == 0 || !VOID_REFUSED && sample < 3);
                    require(owner.hasEffect(MobEffects.LEVITATION) == expected && owner.hasEffect(MobEffects.SLOW_FALLING) == expected
                            && SilentGearRemainingFixture.pushes == (expected ? 1 : 0) && ItemStack.matches(before, root), "Void Ward local threshold/control differs");
                    controls++;
                }
        }
        for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null)) {
            clean(owner);
            ItemStack root = subjects.get(1).copy();
            if (slot == null) { owner.getInventory().setItem(7, root); } else { owner.setItemSlot(slot, root); }
            owner.setPos(8.5, owner.level().getMinY() - 32.5, 8.5); handler.invoke(null, new PlayerTickEvent.Pre(owner));
            require(!owner.hasEffect(MobEffects.LEVITATION) && SilentGearRemainingFixture.pushes == 0, "Held/idle Void Ward activated"); controls++;
        }
        clean(owner);
        owner.setItemSlot(EquipmentSlot.FEET, subjects.get(3)); owner.setItemSlot(EquipmentSlot.CHEST, api.armor("chestplate", "chestplate_plates", "tyrian_steel"));
        owner.setPos(8.5, owner.level().getMinY() - 32.5, 8.5); handler.invoke(null, new PlayerTickEvent.Pre(owner));
        require(owner.hasEffect(MobEffects.LEVITATION) && SilentGearRemainingFixture.pushes == 1, "Independent native chest Void Ward lost");
        REPORT.append("VOID production controls=").append(controls + 1).append(" stock acquisition; strict threshold; both orientations; held/idle negative; native chest positive; unchanged stacks\n");
        if (!VOID_VARIANT.isEmpty()) {
            clean(owner);
            ItemStack gold = NeoForgeSilentGearTest.workbench(owner, api.boot("gold"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            require(gold.getItem().makesPiglinsNeutral(gold, owner), "Void variant disabled independent Brilliant");
            ItemStack moonSource = api.boot("azure_silver");
            ItemStack moon = NeoForgeSilentGearTest.workbench(owner, moonSource, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            owner.setItemSlot(EquipmentSlot.FEET, moon);
            var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(owner, 12, 1);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fall);
            require(Math.abs(fall.getDistance() - 12 * (1 - .15F * api.traitLevel(moonSource, "moonwalker"))) < 1E-5,
                    "Void variant disabled independent Moonwalker");
            require(SilentGearRecognition.voidWard(true, new PlayerTickEvent.Pre(owner), false), "Void refusal discarded an original true result");
            REPORT.append("VOID ").append(VOID_VARIANT).append(": native rescue/predicates and production Brilliant/Moonwalker remain; original true preserved\n");
            clean(owner); owner.connection.teleport(8.5, 85, 8.5, 0, 0); return;
        }

        int cures = 0;
        for (String name : List.of("cure_poison", "cure_wither")) {
            var samples = new ArrayList<ItemStack>(); ItemStack source = SilentGearRemainingFixture.injected(api, name); samples.add(source);
            samples.addAll(NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS)));
            for (int sample = 0; sample < samples.size(); sample++) {
                for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null)) {
                    clean(owner);
                    ItemStack root = samples.get(sample).copy(), before = root.copy();
                    if (slot != null) { owner.setItemSlot(slot, root); } else { owner.getInventory().setItem(7, root); }
                    effects(owner); if (sample > 0) { SilentGearBounceTest.protectedRoot = root; }
                    root.inventoryTick(owner.level(), owner, slot);
                    require(owner.hasEffect(MobEffects.POISON) == !(slot != null && name.equals("cure_poison"))
                            && owner.hasEffect(MobEffects.WITHER) == !(slot != null && name.equals("cure_wither"))
                            && owner.hasEffect(MobEffects.NIGHT_VISION) && ItemStack.matches(before, root), "Cure selected/native control differs");
                    if (sample > 0 && slot != null) { require(SilentGearRemainingFixture.cureCalls.get(name) == 1, "Duplicate cure call"); }
                    cures++;
                }
            }
        }
        clean(owner);
        ItemStack mixed = NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingFixture.injected(api, "cure_poison"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        var nonPlayer = new net.minecraft.world.entity.animal.pig.Pig(net.minecraft.world.entity.EntityType.PIG, owner.level());
        effects(nonPlayer); mixed.inventoryTick(owner.level(), nonPlayer, EquipmentSlot.FEET);
        require(nonPlayer.hasEffect(MobEffects.POISON) && nonPlayer.hasEffect(MobEffects.WITHER)
                && nonPlayer.hasEffect(MobEffects.NIGHT_VISION), "Non-player cure activated");
        REPORT.append("CURE synchronous controls=").append(cures + 1).append(" synthetic native/computed acquisition; both traits/orientations; feet/main/offhand/idle; non-player negative; unrelated effect intact\n");
        oldPoisonEffect = cureEffect(api, "cure_poison");
        var targets = oldPoisonEffect.getClass().getDeclaredField("effects"); targets.setAccessible(true);
        targets.set(oldPoisonEffect, List.of(MobEffects.WITHER)); // Reload must replace this deliberately stale receiver.
        clean(owner); owner.connection.teleport(8.5, 85, 8.5, 0, 0);
    }

    private static Object cureEffect(NeoForgeSilentGearTest.NativeApi api, String id) throws Exception {
        Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:" + id);
        Object definition = ((java.util.function.Supplier<?>) ref).get();
        return ((List<?>) definition.getClass().getMethod("getEffects").invoke(definition)).getFirst();
    }

    private static void reloadControl(ServerPlayer owner) throws Exception {
        var api = new NeoForgeSilentGearTest.NativeApi();
        require(cureEffect(api, "cure_poison") != oldPoisonEffect, "Actual reload retained the old cure receiver");
        ItemStack source = SilentGearRemainingFixture.injected(api, "cure_poison");
        for (ItemStack root : NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS))) {
            ItemStack before = root.copy(); effects(owner); root.inventoryTick(owner.level(), owner, EquipmentSlot.MAINHAND);
            require(!owner.hasEffect(MobEffects.POISON) && owner.hasEffect(MobEffects.WITHER) && owner.hasEffect(MobEffects.NIGHT_VISION)
                    && ItemStack.matches(before, root), "Reload reused a stale cure receiver or changed stacks");
        }
        REPORT.append("CURE actual datapack reload: fresh receiver in both orientations; stale target not reused; unrelated effects and source history retained\n");
        clean(owner);
    }

    private static void definitionControl(ServerPlayer owner) throws Exception {
        clean(owner);
        var api = new NeoForgeSilentGearTest.NativeApi();
        ItemStack root = NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingFixture.injected(api, "cure_poison"),
                SilentGearRemainingFixture.injected(api, "cure_wither")).getFirst(), before = root.copy();
        Object effect = cureEffect(api, "cure_poison");
        var targets = effect.getClass().getDeclaredField("effects"); targets.setAccessible(true); Object original = targets.get(effect);
        try {
            targets.set(effect, List.of(MobEffects.NIGHT_VISION));
            effects(owner); root.inventoryTick(owner.level(), owner, EquipmentSlot.FEET);
            require(owner.hasEffect(MobEffects.POISON) && !owner.hasEffect(MobEffects.WITHER) && owner.hasEffect(MobEffects.NIGHT_VISION)
                    && ItemStack.matches(before, root), "Changed Poison definition escaped admission or disabled Wither");
        } finally { targets.set(effect, original); }
        effects(owner); root.inventoryTick(owner.level(), owner, EquipmentSlot.FEET);
        require(owner.hasEffect(MobEffects.POISON) && !owner.hasEffect(MobEffects.WITHER) && owner.hasEffect(MobEffects.NIGHT_VISION),
                "Refused Poison retried or disabled independent Wither");
        ItemStack snow = NeoForgeSilentGearTest.workbench(owner, api.boot("leather"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        require(snow.canWalkOnPowderedSnow(owner), "Poison refusal disabled Snow Walker");
        ItemStack ward = NeoForgeSilentGearTest.workbench(owner, api.boot("tyrian_steel"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        owner.setItemSlot(EquipmentSlot.FEET, ward);
        require(SilentGearRecognition.voidWard(false, new PlayerTickEvent.Pre(owner), true), "Poison refusal disabled Void Ward");
        REPORT.append("CURE changed Poison target: refused independently before callback; no replay after restore; Wither/Snow Walker/Void Ward remain; root/history unchanged\n");
        clean(owner);
    }

    private static final class Session {
        private final ServerPlayer owner;
        private final int mode, sample;
        private final ItemStack root, before;
        private final double startY;
        private int started;

        Session(ServerPlayer owner, int mode, int sample) throws Exception { this(owner, mode, sample, null); }

        Session(ServerPlayer owner, int mode, int sample, ItemStack existing) throws Exception {
            this.owner = owner; this.mode = mode; this.sample = sample; clean(owner);
            var api = new NeoForgeSilentGearTest.NativeApi();
            if (mode == 0) {
                ItemStack source = existing == null ? api.boot("tyrian_steel") : ItemStack.EMPTY;
                root = existing != null ? existing : sample == 0 ? source : NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS)).get(sample - 1);
                startY = owner.level().getMinY() - 32.5;
            } else if (mode == 1) {
                if (existing != null) { root = existing; }
                else if (sample < 2) { root = new ItemStack(sample == 0 ? Items.LEATHER_BOOTS : Items.DIAMOND_BOOTS); }
                else if (sample < 5) { root = api.boot(List.of("leather", "wool", "fine_silk_cloth").get(sample - 2)); }
                else { root = NeoForgeSilentGearTest.workbench(owner, api.boot(List.of("leather", "wool", "fine_silk_cloth").get((sample - 5) / 2)), new ItemStack(Items.DIAMOND_BOOTS)).get((sample - 5) % 2); }
                for (int x = 5; x <= 11; x++) { for (int z = 5; z <= 11; z++) {
                    owner.level().setBlock(new BlockPos(x, 78, z), Blocks.STONE.defaultBlockState(), 3);
                    owner.level().setBlock(new BlockPos(x, 80, z), Blocks.POWDER_SNOW.defaultBlockState(), 3);
                } }
                startY = 82;
            } else {
                ItemStack both = existing == null ? SilentGearRemainingFixture.injected(api, "cure_poison", "cure_wither") : ItemStack.EMPTY;
                root = existing != null ? existing : sample == 0 ? both : sample == 3
                        ? NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingFixture.injected(api, "cure_poison"), SilentGearRemainingFixture.injected(api, "cure_poison")).getFirst()
                        : NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingFixture.injected(api, "cure_poison"), SilentGearRemainingFixture.injected(api, "cure_wither")).get(sample == 2 ? 1 : 0);
                startY = 85;
            }
            before = root.copy();
            if (mode == 2 && sample == 4) { owner.getInventory().setItem(7, root); }
            else { owner.setItemSlot(mode == 2 && sample == 2 ? EquipmentSlot.MAINHAND : mode == 2 && sample == 3 ? EquipmentSlot.OFFHAND : EquipmentSlot.FEET, root); }
            if (ModContent.getMixedBootsData(root).isPresent()) { SilentGearBounceTest.protectedRoot = root; }
            if (mode == 0) {
                Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:void_ward");
                require((boolean) api.helper.getMethod("hasTraitArmor", Player.class, api.resource).invoke(null, owner, ref) == (sample == 0),
                        "Connected Void Ward control has an unexpected native armor contributor");
            }
            if (mode == 2) { effects(owner); }
            owner.connection.teleport(8.5, startY, 8.5, 0, 0); started = owner.tickCount;
        }

        String release() { owner.setNoGravity(false); started = owner.tickCount; return poll(); }

        String poll() {
            require(owner.tickCount - started < 200, "Session timeout mode=" + mode + " sample=" + sample + " y=" + owner.getY() + " effects=" + owner.getActiveEffects());
            require(ItemStack.matches(before, root), "Remaining-effects tick changed root/source/state");
            var data = new JsonObject(); data.add("root", ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow());
            data.addProperty("startY", startY); data.addProperty("y", owner.getY()); data.addProperty("age", owner.tickCount - started);
            data.addProperty("rescued", owner.hasEffect(MobEffects.LEVITATION) && owner.hasEffect(MobEffects.SLOW_FALLING));
            data.addProperty("poison", owner.hasEffect(MobEffects.POISON)); data.addProperty("wither", owner.hasEffect(MobEffects.WITHER)); data.addProperty("night", owner.hasEffect(MobEffects.NIGHT_VISION));
            return data.toString();
        }

        String finish(double clientY) {
            require(ItemStack.matches(before, root), "Completion changed root/source/state");
            if (mode == 0) {
                require(SilentGearRemainingFixture.pushes > 0 && SilentGearRemainingFixture.pushes == SilentGearRemainingFixture.queries, "Void rescue duplicated native pushes");
                REPORT.append("VOID connected ").append(sample).append(" native pushes/eligible ticks=").append(SilentGearRemainingFixture.pushes)
                        .append(" serverY=").append(owner.getY()).append(" clientY=").append(clientY).append("; native motion/potions and unchanged stack\n");
            } else if (mode == 1) {
                if (sample == 0 || sample >= 5) { descending(owner); }
                REPORT.append("SNOW connected ").append(sample).append(" production")
                        .append(" serverY=").append(owner.getY()).append(" clientY=").append(clientY).append("; natural gravity/collision, callback and descending controls\n");
            } else {
                if (sample > 0 && sample < 4) {
                    var expectedIds = sample == 3 ? java.util.Set.of("cure_poison") : java.util.Set.of("cure_poison", "cure_wither");
                    require(SilentGearRemainingFixture.cureCalls.keySet().equals(expectedIds) && SilentGearRemainingFixture.cureTicks > 0
                            && SilentGearRemainingFixture.cureCalls.values().stream().allMatch(value -> value == SilentGearRemainingFixture.cureTicks), "Duplicate/missing per-ID cure dispatch");
                }
                REPORT.append("CURE connected ").append(sample).append(" ordinary ticks=").append(owner.tickCount - started)
                        .append(" production calls=").append(SilentGearRemainingFixture.cureCalls).append("; real inventory selection/effect packets; unchanged root/sources\n");
                if (sample == 4) {
                    try { definitionControl(owner); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }
            }
            NetworkAcceptanceFiles.write(reportOutput + "-report.txt", REPORT.toString());
            clean(owner); owner.connection.teleport(8.5, 85, 8.5, 0, 0); return "{}";
        }
    }
}
