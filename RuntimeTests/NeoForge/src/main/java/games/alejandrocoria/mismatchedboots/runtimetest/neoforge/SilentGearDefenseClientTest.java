package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.close;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Ordinary connected landings; JSON crosses threads, never stacks or synthetic rebound motion. */
final class SilentGearDefenseClientTest {
    private static Future<String> job;
    private static JsonObject state;
    private static int index, clientSounds, engineSounds;
    private static boolean listening, released, finishing, landingsComplete;
    private static double minimumY = Double.MAX_VALUE, reboundY;
    private static final StringBuilder report = new StringBuilder();
    // Integrated-server working state: accessed only by submitted jobs/server event callbacks.
    private static Session session;
    private static ItemStack persisted;
    private static int nativeClientSounds, nativeEngineSounds;
    private static Future<String> healthJob;
    private static JsonObject healthState;
    private static Health health;
    private static int healthIndex;
    private static boolean healthCleanup, healthComplete;
    private static final StringBuilder healthReport = new StringBuilder();

    private SilentGearDefenseClientTest() {}

    static void captureRejoin(ServerPlayer owner) throws Exception {
        var saved = JsonParser.parseString(Files.readString(manifest())).getAsJsonObject();
        require(saved.get("process").getAsLong() != ProcessHandle.current().pid()
                && saved.get("uuid").getAsString().equals(owner.getUUID().toString()), "Defensive rejoin reused process/changed UUID");
        persisted = owner.getInventory().getItem(12);
        nativeClientSounds = saved.get("nativeClientSounds").getAsInt(); nativeEngineSounds = saved.get("nativeEngineSounds").getAsInt();
        var expected = ItemStack.CODEC.parse(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), saved.get("root")).getOrThrow();
        require(ItemStack.matches(expected, persisted) && persisted.getDamageValue() > 0,
                "Actual used defensive player root/source/SG/JDT state changed on rejoin");
        // Capture the actual loaded object before the unrelated magnet scenarios clear their test inventory.
    }

    static boolean advance(Minecraft client, boolean rejoin) throws Exception {
        if (landingsComplete) { return rejoin || health(client); }
        listen();
        if (job != null) {
            if (!job.isDone()) { return false; }
            state = JsonParser.parseString(job.get()).getAsJsonObject(); job = null;
        }
        var server = client.getSingleplayerServer(); var uuid = client.player.getUUID();
        if (state == null) {
            job = server.submit(() -> {
                try { session = new Session(server.getPlayerList().getPlayer(uuid), rejoin ? 3 : index, rejoin); return session.poll(); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }); return false;
        }
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var expected = ItemStack.CODEC.parse(ops, state.get("root")).getOrThrow();
        if (finishing) {
            var actual = state.get("stored").getAsBoolean() ? client.player.getInventory().getItem(12) : client.player.getItemBySlot(EquipmentSlot.FEET);
            if (!ItemStack.matches(expected, actual)) { job = server.submit(() -> session.poll()); return false; }
            var before = actual.copy(); actual.getAttributeModifiers(); ModContent.getMixedBootsData(actual); actual.get(SilentGearCompatibility.state());
            require(ItemStack.matches(before, actual), "Defensive client queries changed the received used root");
            report.append(rejoin ? "used player root after rejoin" : "landing " + index).append(": server/client rebound; client rise >= ").append(reboundY)
                    .append("; native sound requests=").append(clientSounds).append(" engine=").append(engineSounds)
                    .append("; one server bill=").append(state.get("bill").getAsInt()).append(" actual wear=").append(state.get("wear").getAsInt())
                    .append("; complete used-root/source/state packets match\n");
            NetworkAcceptanceFiles.write("silent-gear-defense-" + (rejoin ? "rejoin" : "client") + "-report.txt", report.toString());
            finishing = false;
            if (rejoin || ++index == 4) { landingsComplete = true; }
            else { state = null; released = false; }
            return false;
        }
        if (!released) {
            if (!ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET)) || client.player.getY() < 94) { return false; }
            clientSounds = engineSounds = 0; minimumY = Double.MAX_VALUE; reboundY = 0;
            released = true; job = server.submit(() -> session.release()); return false;
        }
        minimumY = Math.min(minimumY, client.player.getY());
        if (minimumY <= 81.05 && client.player.getY() > 81.05) { reboundY = Math.max(reboundY, client.player.getY() - 81); }
        if (!state.get("landed").getAsBoolean() || state.get("serverY").getAsDouble() < 81.15 || reboundY < .15 || clientSounds == 0) {
            job = server.submit(() -> session.poll()); return false;
        }
        int sounds = clientSounds, engine = engineSounds; double rise = reboundY;
        job = server.submit(() -> session.finish(sounds, engine, rise));
        finishing = true;
        return false;
    }

    private static void listen() {
        if (listening) { return; } listening = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingFallEvent event) -> {
            if (!event.getEntity().level().isClientSide() && session != null && event.getEntity() == session.owner && event.getDistance() > 3) {
                session.fall = event;
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Native connected Bounce sound parameters differ");
                if (event.getLevel().isClientSide()) { clientSounds++; }
                else if (session != null) { session.serverSounds++; }
            }
        });
        NeoForge.EVENT_BUS.addListener((PlaySoundEvent event) -> {
            if (event.getOriginalSound().getIdentifier().equals(SoundEvents.SLIME_BLOCK_FALL.location())) { engineSounds++; }
        });
    }

    private static Path manifest() { return Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "silent-gear-defense-player.json"); }

    private static void saveUsed(ServerPlayer owner) {
        var saved = new JsonObject(); saved.addProperty("process", ProcessHandle.current().pid()); saved.addProperty("uuid", owner.getUUID().toString());
        saved.add("root", ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), persisted).getOrThrow());
        saved.addProperty("nativeClientSounds", nativeClientSounds); saved.addProperty("nativeEngineSounds", nativeEngineSounds);
        NetworkAcceptanceFiles.write("silent-gear-defense-player.json", saved.toString());
    }

    private static boolean health(Minecraft client) throws Exception {
        if (healthComplete) { return true; }
        if (healthJob != null) {
            if (!healthJob.isDone()) { return false; }
            healthState = JsonParser.parseString(healthJob.get()).getAsJsonObject(); healthJob = null;
            if (healthCleanup) {
                healthCleanup = false;
                if (healthIndex == 4) { healthComplete = true; return true; }
                healthState = null;
            }
        }
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        if (healthState == null) {
            healthJob = server.submit(() -> {
                try { health = new Health(server.getPlayerList().getPlayer(id), healthIndex); return health.poll(); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }); return false;
        }
        var expected = ItemStack.CODEC.parse(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), healthState.get("root")).getOrThrow();
        if (!ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET))
                || Math.abs(client.player.getHealth() - healthState.get("health").getAsFloat()) > 1E-5) {
            String observation = "client rootMatch=" + ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET))
                    + " health=" + client.player.getHealth() + " expected=" + healthState.get("health");
            healthJob = server.submit(() -> health.poll(observation)); return false;
        }
        int stage = healthState.get("stage").getAsInt();
        if (stage < 3) { healthJob = server.submit(() -> health.hit()); return false; }
        var attacker = client.level.getEntity(healthState.get("attacker").getAsInt());
        if (attacker == null || healthIndex > 0 && attacker.getX() < healthState.get("startX").getAsDouble() + .15) {
            String observation = "client attacker=" + (attacker == null ? "missing" : attacker.position());
            healthJob = server.submit(() -> health.poll(observation)); return false;
        }
        if (healthIndex == 0) { close(attacker.getX(), healthState.get("startX").getAsDouble(), "Vanilla attacker no-retaliation control"); }
        healthReport.append("health ").append(healthIndex).append(": actual lava/wall/melee health packets; native incoming fire=")
                .append(healthIndex == 0 ? 10 : 8.4).append(" wall=").append(healthIndex == 0 ? 10 : 8)
                .append("; real zombie attack, server knockback=").append(healthIndex == 0 ? 0 : 2)
                .append(" and client entity motion; unchanged root/sources\n");
        NetworkAcceptanceFiles.write("silent-gear-defense-health-report.txt", healthReport.toString());
        healthJob = server.submit(() -> health.clean()); healthCleanup = true; healthIndex++; return false;
    }

    /** Numeric armor is neutralized on the actor, not confused with the selected native incoming effects. */
    private static final class Health {
        private final ServerPlayer owner;
        private final ItemStack root, before;
        private final int sample, started;
        private final double armorBase, toughnessBase;
        private int stage;
        private Zombie attacker;
        private String clientObservation = "awaiting packets";

        private Health(ServerPlayer owner, int sample) throws Exception {
            this.owner = owner; this.sample = sample; started = owner.tickCount;
            owner.removeAllEffects(); owner.setHealth(owner.getMaxHealth()); owner.setAbsorptionAmount(0); owner.setNoGravity(true);
            for (var slot : EquipmentSlot.values()) { owner.setItemSlot(slot, ItemStack.EMPTY); }
            owner.connection.teleport(8.5, 83, 8.5, 0, 0);
            var api = new NeoForgeSilentGearTest.NativeApi();
            var boots = SilentGearBounceTest.lined(api.boot("crimson_iron"), api, owner);
            root = sample == 0 ? new ItemStack(Items.DIAMOND_BOOTS) : sample == 1 ? boots
                    : NeoForgeSilentGearTest.workbench(owner, boots, new ItemStack(Items.DIAMOND_BOOTS)).get(sample - 2);
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); before = root.copy(); owner.setItemSlot(EquipmentSlot.FEET, root);
            armorBase = owner.getAttribute(Attributes.ARMOR).getBaseValue(); toughnessBase = owner.getAttribute(Attributes.ARMOR_TOUGHNESS).getBaseValue();
        }

        private String hit() {
            if (stage == 0) {
                owner.getAttribute(Attributes.ARMOR).setBaseValue(armorBase - owner.getAttributeValue(Attributes.ARMOR));
                owner.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(toughnessBase - owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                close(owner.getAttributeValue(Attributes.ARMOR), 0, "Connected armor control");
                close(owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0, "Connected toughness control");
            }
            owner.setHealth(owner.getMaxHealth()); owner.invulnerableTime = 0;
            if (stage < 2) {
                require(owner.hurtServer(owner.level(), stage == 0 ? owner.damageSources().lava() : owner.damageSources().flyIntoWall(), 10), "Connected defensive hit rejected");
                close(owner.getMaxHealth() - owner.getHealth(), sample == 0 ? 10 : stage == 0 ? 8.4 : 8, "Connected native incoming health");
            } else {
                owner.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
                // No goals, but keep ordinary travel: NoAI also suppresses movement and would hide real knockback packets.
                attacker = new Zombie(EntityType.ZOMBIE, owner.level()) { @Override protected void registerGoals() {} };
                attacker.setNoGravity(true); attacker.setInvulnerable(true);
                attacker.setPos(10.5, 83, 8.5); attacker.setYRot(90); attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
                require(owner.level().addFreshEntity(attacker) && attacker.doHurtTarget(owner.level(), owner), "Real connected zombie attack rejected");
                close(attacker.getDeltaMovement().x, sample == 0 ? 0 : 2, "Connected native retaliation strength");
                close(owner.getMaxHealth() - owner.getHealth(), 4, "Melee is not an incoming fire/wall reduction");
            }
            stage++; return poll();
        }

        private String poll(String observation) { clientObservation = observation; return poll(); }

        private String poll() {
            require(owner.tickCount - started < 100, "Connected defensive health/retaliation timeout sample=" + sample + " stage=" + stage
                    + " serverAttacker=" + (attacker == null ? "none" : attacker.position()) + " " + clientObservation);
            require(ItemStack.matches(before, root), "Connected damage changed protected root/source state");
            owner.inventoryMenu.broadcastChanges();
            var result = new JsonObject(); result.addProperty("stage", stage); result.addProperty("health", owner.getHealth());
            result.add("root", ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow());
            if (attacker != null) { result.addProperty("attacker", attacker.getId()); result.addProperty("startX", 10.5); }
            return result.toString();
        }

        private String clean() {
            attacker.discard(); owner.getAttribute(Attributes.ARMOR).setBaseValue(armorBase); owner.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(toughnessBase);
            if (sample == 3) { saveUsed(owner); }
            owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); return "{}";
        }
    }

    private static final class Session {
        private final ServerPlayer owner;
        private final ItemStack root, before;
        private final int sample;
        private final int started;
        private LivingFallEvent fall;
        private int serverSounds;
        private boolean finished;

        private Session(ServerPlayer owner, int sample, boolean rejoin) throws Exception {
            this.owner = owner; this.sample = sample;
            owner.removeAllEffects(); owner.getFoodData().setFoodLevel(10); owner.setHealth(owner.getMaxHealth()); owner.setAbsorptionAmount(0);
            for (var slot : EquipmentSlot.values()) { owner.setItemSlot(slot, ItemStack.EMPTY); }
            // Retain the unrelated saved magnet root in slot 11; the previously captured rejoin root is server-owned.
            owner.setNoGravity(true);
            for (int x = 3; x < 14; x++) { for (int z = 3; z < 14; z++) { owner.level().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3); } }
            var api = new NeoForgeSilentGearTest.NativeApi(); SilentGearTraitSpike.initialize(api);
            if (rejoin) { root = persisted; }
            else {
                var nativeBoot = SilentGearBounceTest.lined(api.boot("crimson_iron"), api, owner);
                if (sample == 0) { root = nativeBoot; }
                else if (sample < 3) { root = NeoForgeSilentGearTest.workbench(owner, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)).get(sample - 1); }
                else {
                    var energyBoot = NeoForgeJustDireNegateFallTest.boots("eclipsealloy", 4_000);
                    JustDireAttributeTestSupport.set(energyBoot, "groundstomp_upgrade_installed", true);
                    root = NeoForgeSilentGearTest.workbench(owner, nativeBoot, energyBoot).getFirst();
                    require(JustDireResourceTestSupport.handler(root).getAmountAsInt() == 2_000, "Joint landing did not average initial FE");
                    var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                            .getConstructor(String.class, int.class, boolean.class, boolean.class);
                    JustDireAttributeTestSupport.set(root, "ability_bindings", java.util.List.of(binding.newInstance("groundstomp", 72, false, true)));
                }
            }
            before = root.copy(); owner.setItemSlot(EquipmentSlot.FEET, root);
            // Calibrate the actual native writer before guarding only this live mixed root.
            var witness = api.boot("iron"); SilentGearBounceTest.protectedRoot = witness; boolean caught = false;
            try { SilentGearTraitSpike.nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, witness, owner); }
            catch (InvocationTargetException failure) { caught = failure.getCause() instanceof AssertionError; }
            finally { SilentGearBounceTest.protectedRoot = null; }
            require(caught, "Connected recalculation witness was not calibrated");
            if (sample != 0) { SilentGearBounceTest.protectedRoot = root; }
            SilentGearBounceTest.wearCalls = 0;
            owner.fallDistance = 0; owner.connection.teleport(8.5, 95, 8.5, 0, 0); started = owner.tickCount;
        }

        private String release() { owner.setNoGravity(false); return poll(); }

        private String poll() {
            require(owner.tickCount - started < 180, "Connected Bounce timeout sample=" + sample + " y=" + owner.getY()
                    + " motion=" + owner.getDeltaMovement() + " landed=" + (fall != null) + " canceled=" + (fall != null && fall.isCanceled()) + " sounds=" + serverSounds);
            var result = new JsonObject(); result.add("root", encode()); result.addProperty("landed", fall != null);
            result.addProperty("serverY", owner.getY());
            result.addProperty("stored", finished && sample == 3);
            result.addProperty("wear", root.getDamageValue() - before.getDamageValue());
            result.addProperty("bill", fall == null ? 0 : (int) (fall.getDistance() / 3) - 1);
            return result.toString();
        }

        private String finish(int sounds, int engine, double rise) {
            if (sample == 0) { nativeClientSounds = sounds; nativeEngineSounds = engine; }
            require(fall != null && fall.isCanceled() && serverSounds == 1 && sounds == nativeClientSounds
                            && engine == nativeEngineSounds && engine == 1 && rise >= .15,
                    "Connected native rebound/sound differs: sample=" + sample + " serverSounds=" + serverSounds + " clientRequests=" + sounds + " engine=" + engine);
            int bill = (int) (fall.getDistance() / 3) - 1, wear = root.getDamageValue() - before.getDamageValue();
            require(owner.getHealth() == owner.getMaxHealth() && wear > 0 && wear <= bill, "Landing health or ordinary wear differs");
            if (sample != 0) {
                require(SilentGearBounceTest.wearCalls == 1 && SilentGearBounceTest.requestedWear == bill
                        && ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(before)), "Landing duplicated charge or mutated source histories");
            }
            if (sample == 3) {
                require(JustDireResourceTestSupport.handler(root).getAmountAsInt() == JustDireResourceTestSupport.handler(before).getAmountAsInt(), "Bounce also debited JDT FE");
                persisted = root; owner.getInventory().setItem(12, root); owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                // Initial save expectations are published after the remaining connected controls settle live state.
                if (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRejoin")) { saveUsed(owner); }
            }
            finished = true; SilentGearBounceTest.protectedRoot = null; owner.setNoGravity(true); owner.connection.teleport(8.5, 83, 8.5, 0, 0);
            return poll();
        }

        private com.google.gson.JsonElement encode() { return ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow(); }
    }
}
