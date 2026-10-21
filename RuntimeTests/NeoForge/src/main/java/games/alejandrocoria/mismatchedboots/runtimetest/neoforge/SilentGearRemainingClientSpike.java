package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
final class SilentGearRemainingClientSpike {
    private static final String OUTPUT = "silent-gear-remaining";
    private static final BlockPos SNOW = new BlockPos(8, 80, 8);
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks, mode, index;
    private static boolean finished, initialized, released;
    private static Future<String> job;
    private static JsonObject state;
    private static Session session; // Integrated-server jobs only.

    private SilentGearRemainingClientSpike() {}

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
            if (state == null || !state.has("root")) {
                int nextMode = mode, nextIndex = index;
                job = server.submit(() -> {
                    try { session = new Session(server.getPlayerList().getPlayer(uuid), nextMode, nextIndex); return session.poll(); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }); return;
            }
            ItemStack expected = ItemStack.CODEC.parse(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), state.get("root")).getOrThrow();
            ItemStack received = mode == 2 ? held(client.player, index) : client.player.getItemBySlot(EquipmentSlot.FEET);
            if (!ItemStack.matches(expected, received)) { job = server.submit(() -> session.poll()); return; }
            if (mode == 0) {
                if (!state.get("rescued").getAsBoolean() || !client.player.hasEffect(MobEffects.LEVITATION)
                        || !client.player.hasEffect(MobEffects.SLOW_FALLING) || client.player.getY() < state.get("startY").getAsDouble() + 1
                        || state.get("y").getAsDouble() < state.get("startY").getAsDouble() + 1) {
                    job = server.submit(() -> session.poll()); return;
                }
            } else if (mode == 1) {
                if (!released) {
                    if (Math.abs(client.player.getY() - 82) > .1) { return; }
                    released = true; job = server.submit(() -> session.release()); return;
                }
                if (state.get("age").getAsInt() < 45) { job = server.submit(() -> session.poll()); return; }
                boolean supported = index == 0 || index >= 7;
                require((client.player.getY() >= 80.95) == supported && (state.get("y").getAsDouble() >= 80.95) == supported,
                        "Powder-snow real collision differs index=" + index + " server=" + state + " clientY=" + client.player.getY());
                require(received.canWalkOnPowderedSnow(client.player) == supported, "Client snow callback differs");
                if (supported) { descending(client.player); }
            } else {
                if (state.get("age").getAsInt() < 10) { job = server.submit(() -> session.poll()); return; }
                boolean poison = index == 4, wither = index >= 3;
                require(state.get("poison").getAsBoolean() == poison && state.get("wither").getAsBoolean() == wither
                        && state.get("night").getAsBoolean(), "Ordinary cure server ticks differ: " + state);
                if (client.player.hasEffect(MobEffects.POISON) != poison || client.player.hasEffect(MobEffects.WITHER) != wither
                        || !client.player.hasEffect(MobEffects.NIGHT_VISION)) { job = server.submit(() -> session.poll()); return; }
            }
            int doneMode = mode; double clientY = client.player.getY();
            job = server.submit(() -> session.finish(clientY));
            if (++index == (doneMode == 0 ? 3 : doneMode == 1 ? 9 : 5)) { mode++; index = 0; }
            released = false;
            if (mode == 3) {
                // Finish the final server assertion before declaring success/stopping the client.
                mode = 4;
            }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop(); }
    }

    private static void complete(Minecraft client) {
        try {
            job.get(); job = null;
            NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "native controls and research-only mixed candidates\n");
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
        SilentGearRemainingSpike.queries = SilentGearRemainingSpike.pushes = SilentGearRemainingSpike.cureTicks = 0;
        SilentGearRemainingSpike.cureCalls.clear();
    }

    private static void effects(Player player) {
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
        for (boolean enabled : List.of(false, true)) {
            for (int sample = 0; sample < subjects.size(); sample++) {
                for (boolean below : List.of(false, true)) {
                    clean(owner); SilentGearRemainingSpike.enabled = enabled;
                    ItemStack root = subjects.get(sample).copy(), before = root.copy(); owner.setItemSlot(EquipmentSlot.FEET, root);
                    owner.setPos(8.5, owner.level().getMinY() - 32 - (below ? .5 : 0), 8.5);
                    if (ModContent.getMixedBootsData(root).isPresent()) { SilentGearBounceTest.protectedRoot = root; }
                    handler.invoke(null, new PlayerTickEvent.Pre(owner));
                    boolean expected = below && (sample == 0 || enabled && sample < 3);
                    require(owner.hasEffect(MobEffects.LEVITATION) == expected && owner.hasEffect(MobEffects.SLOW_FALLING) == expected
                            && SilentGearRemainingSpike.pushes == (expected ? 1 : 0) && ItemStack.matches(before, root), "Void Ward local threshold/control differs");
                    controls++;
                }
            }
        }
        for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null)) {
            clean(owner); SilentGearRemainingSpike.enabled = true;
            ItemStack root = subjects.get(1).copy();
            if (slot == null) { owner.getInventory().setItem(7, root); } else { owner.setItemSlot(slot, root); }
            owner.setPos(8.5, owner.level().getMinY() - 32.5, 8.5); handler.invoke(null, new PlayerTickEvent.Pre(owner));
            require(!owner.hasEffect(MobEffects.LEVITATION) && SilentGearRemainingSpike.pushes == 0, "Held/idle Void Ward activated"); controls++;
        }
        clean(owner); SilentGearRemainingSpike.enabled = false;
        owner.setItemSlot(EquipmentSlot.FEET, subjects.get(3)); owner.setItemSlot(EquipmentSlot.CHEST, api.armor("chestplate", "chestplate_plates", "tyrian_steel"));
        owner.setPos(8.5, owner.level().getMinY() - 32.5, 8.5); handler.invoke(null, new PlayerTickEvent.Pre(owner));
        require(owner.hasEffect(MobEffects.LEVITATION) && SilentGearRemainingSpike.pushes == 1, "Independent native chest Void Ward lost");
        REPORT.append("VOID controls=").append(controls + 1).append(" stock acquisition; strict threshold; native/off/on mixed; held/idle negative; native chest positive; unchanged stacks\n");

        int cures = 0;
        for (String name : List.of("cure_poison", "cure_wither")) {
            var samples = new ArrayList<ItemStack>(); ItemStack source = SilentGearRemainingSpike.injected(api, name); samples.add(source);
            samples.addAll(NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS)));
            for (int sample = 0; sample < samples.size(); sample++) {
                for (EquipmentSlot slot : Arrays.asList(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null)) {
                    clean(owner); SilentGearRemainingSpike.enabled = true;
                    ItemStack root = samples.get(sample).copy(), before = root.copy();
                    if (slot != null) { owner.setItemSlot(slot, root); } else { owner.getInventory().setItem(7, root); }
                    effects(owner); if (sample > 0) { SilentGearBounceTest.protectedRoot = root; }
                    root.inventoryTick(owner.level(), owner, slot);
                    require(owner.hasEffect(MobEffects.POISON) == !(slot != null && name.equals("cure_poison"))
                            && owner.hasEffect(MobEffects.WITHER) == !(slot != null && name.equals("cure_wither"))
                            && owner.hasEffect(MobEffects.NIGHT_VISION) && ItemStack.matches(before, root), "Cure selected/native control differs");
                    if (sample > 0 && slot != null) { require(SilentGearRemainingSpike.cureCalls.get(name) == 1, "Duplicate cure call"); }
                    cures++;
                }
            }
        }
        clean(owner); SilentGearRemainingSpike.enabled = false;
        ItemStack off = NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingSpike.injected(api, "cure_poison"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        owner.setItemSlot(EquipmentSlot.FEET, off); effects(owner); off.inventoryTick(owner.level(), owner, EquipmentSlot.FEET);
        require(owner.hasEffect(MobEffects.POISON), "Candidate-off mixed cure already works");
        REPORT.append("CURE synchronous controls=").append(cures + 1).append(" synthetic native/computed acquisition; both traits/orientations; feet/main/offhand/idle; candidate-off omission; unrelated effect intact\n");
        clean(owner); owner.connection.teleport(8.5, 85, 8.5, 0, 0);
    }

    private static final class Session {
        private final ServerPlayer owner;
        private final int mode, sample;
        private final ItemStack root, before;
        private final double startY;
        private int started;

        Session(ServerPlayer owner, int mode, int sample) throws Exception {
            this.owner = owner; this.mode = mode; this.sample = sample; clean(owner);
            var api = new NeoForgeSilentGearTest.NativeApi();
            SilentGearRemainingSpike.enabled = mode != 1 || sample >= 7;
            if (mode == 0) {
                ItemStack source = api.boot("tyrian_steel");
                root = sample == 0 ? source : NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS)).get(sample - 1);
                startY = owner.level().getMinY() - 32.5;
            } else if (mode == 1) {
                if (sample < 2) { root = new ItemStack(sample == 0 ? Items.LEATHER_BOOTS : Items.DIAMOND_BOOTS); }
                else if (sample < 5) { root = api.boot(List.of("leather", "wool", "fine_silk_cloth").get(sample - 2)); }
                else { root = NeoForgeSilentGearTest.workbench(owner, api.boot("leather"), new ItemStack(Items.DIAMOND_BOOTS)).get((sample - 5) % 2); }
                for (int x = 5; x <= 11; x++) { for (int z = 5; z <= 11; z++) {
                    owner.level().setBlock(new BlockPos(x, 78, z), Blocks.STONE.defaultBlockState(), 3);
                    owner.level().setBlock(new BlockPos(x, 80, z), Blocks.POWDER_SNOW.defaultBlockState(), 3);
                } }
                startY = 82;
            } else {
                ItemStack both = SilentGearRemainingSpike.injected(api, "cure_poison", "cure_wither");
                root = sample == 0 ? both : sample == 3
                        ? NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingSpike.injected(api, "cure_poison"), SilentGearRemainingSpike.injected(api, "cure_poison")).getFirst()
                        : NeoForgeSilentGearTest.workbench(owner, SilentGearRemainingSpike.injected(api, "cure_poison"), SilentGearRemainingSpike.injected(api, "cure_wither")).get(sample == 2 ? 1 : 0);
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
                require(SilentGearRemainingSpike.pushes > 0 && SilentGearRemainingSpike.pushes == SilentGearRemainingSpike.queries, "Void rescue duplicated native pushes");
                REPORT.append("VOID connected ").append(sample).append(" native pushes/eligible ticks=").append(SilentGearRemainingSpike.pushes)
                        .append(" serverY=").append(owner.getY()).append(" clientY=").append(clientY).append("; native motion/potions and unchanged stack\n");
            } else if (mode == 1) {
                if (sample == 0 || sample >= 7) { descending(owner); }
                REPORT.append("SNOW connected ").append(sample).append(" candidate=").append(SilentGearRemainingSpike.enabled)
                        .append(" serverY=").append(owner.getY()).append(" clientY=").append(clientY).append("; natural gravity/collision, callback and descending controls\n");
            } else {
                if (sample > 0 && sample < 4) {
                    require(SilentGearRemainingSpike.cureTicks > 0 && SilentGearRemainingSpike.cureCalls.values().stream().allMatch(value -> value == SilentGearRemainingSpike.cureTicks), "Duplicate/missing per-ID cure dispatch");
                }
                REPORT.append("CURE connected ").append(sample).append(" ordinary ticks=").append(owner.tickCount - started)
                        .append(" candidate calls=").append(SilentGearRemainingSpike.cureCalls).append("; real inventory selection/effect packets; unchanged root/sources\n");
            }
            NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", REPORT.toString());
            clean(owner); owner.connection.teleport(8.5, 85, 8.5, 0, 0); return "{}";
        }
    }
}
