package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in local research only: native controls and one live-root candidate, never production dispatch. */
final class NeoForgeJustDireEarthquakeSpike {
    private static final String PREFIX = "just-dire-earthquake-spike";
    private static final int COST = 50_000, ACTIVE = 200, COOLDOWN = 800;
    private static Object earthquake;
    private static Method nativeAction, nativeTick, nativeUsable, nativeFilter, recordName, recordTicks, recordActive;
    private static Constructor<?> recordConstructor;
    private static boolean initialized, finished;
    private static int groups, clock, paused, warmup;
    private static ServerPlayer nativePlayer, mixedPlayer;
    private static ItemStack nativeClock, mixedClock;
    private static Mob lateTarget;
    private static final StringBuilder report = new StringBuilder();

    private NeoForgeJustDireEarthquakeSpike() {}

    static void register() { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer())); }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        try {
            if (!initialized) {
                if (++warmup == 1) { server.overworld().setChunkForced(0, 0, true); }
                if (warmup < 200 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) { return; }
                require(warmup < 200, "Fixture entity chunk never became ticking");
                bind(); cases(server); startClock(server); initialized = true; return;
            }
            naturalClock();
            if (clock < ACTIVE + COOLDOWN) { return; }
            record("natural server ticks: real empty-target activation, late eligible mob with enabled/funded boots gets no repeat effect/debit; complete 200 active -> 800 cooldown -> expiry; unequipped pause; disabled/depleted/removed upgrade; codec replacement");
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " Earthquake research groups\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            finished = true; server.overworld().setChunkForced(0, 0, false); server.halt(false);
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); finished = true; server.overworld().setChunkForced(0, 0, false); server.halt(false); }
    }

    private static void bind() throws Exception {
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var methods = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods");
        var timer = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
        earthquake = ability.getField("EARTHQUAKE").get(null);
        nativeAction = methods.getMethod("earthquake", Level.class, net.minecraft.world.entity.player.Player.class, ItemStack.class);
        nativeFilter = methods.getMethod("isValidEarthquake", Entity.class);
        nativeTick = tool.getMethod("tickCooldowns", Level.class, ItemStack.class, net.minecraft.world.entity.player.Player.class);
        nativeUsable = tool.getMethod("canUseAbility", ItemStack.class, ability);
        recordConstructor = timer.getConstructor(String.class, int.class, boolean.class);
        recordName = timer.getMethod("abilityName"); recordTicks = timer.getMethod("cooldownTicks"); recordActive = timer.getMethod("isactive");
        var params = tool.getMethod("getAbilityParams", ability).invoke(boots(0).getItem(), earthquake);
        require((int) ability.getMethod("getFeCost").invoke(earthquake) == COST
                && params.getClass().getField("activeCooldown").getInt(params) == ACTIVE
                && params.getClass().getField("cooldown").getInt(params) == COOLDOWN, "Pinned native cost/clock changed");
    }

    private static void cases(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        for (int charge : new int[]{0, 49_999, 50_000, 50_001, 99_999, 100_000, 150_000}) {
            var nativeResult = use(player, boots(charge), false, 3, false, -1);
            for (boolean inverse : new boolean[]{false, true}) {
                var root = mixed(charge, inverse, false); var before = root.copy();
                var result = use(player, root, true, 3, false, -1);
                require(result.equals(nativeResult), "Threshold/exhaustion differs: " + charge + ": " + nativeResult + " vs " + result);
                require(result.processed == Math.min(3, charge / COST), "Wrong processed-target count at FE=" + charge + ": " + result);
                unchangedHistory(before, root);
            }
        }
        record("native/mixed both orientations: 0/49999/50000/50001/99999/100000/150000 FE, three targets; per-target exhaustion and actual Slowness IV/200; false native return is not rejection");
        var emptyNative = use(player, boots(COST), false, 0, false, -1);
        var emptyMixed = use(player, mixed(COST, false, false), true, 0, false, -1);
        require(emptyNative.equals(emptyMixed) && emptyMixed.energy == COST && emptyMixed.timer.equals(new Timer(ACTIVE, true)), "Empty activation skipped lockout or billed FE");
        record("empty target list still needs 50000 initial FE and creates active lockout without debit");
        for (int unbreaking : new int[]{3, 10}) {
            var nativeRoot = boots(100_000); var root = mixed(100_000, false, false);
            var enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            nativeRoot.enchant(enchantment, unbreaking); root.enchant(enchantment, unbreaking);
            require(use(player, nativeRoot, false, 4, false, -1).equals(use(player, root, true, 4, false, -1)), "Native discount/eligibility differs");
            record("Unbreaking " + unbreaking + ": native per-target discount, full base eligibility, no invented wear/resource cost");
        }
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            player.setGameMode(mode);
            for (int charge : new int[]{49_999, COST}) {
                var original = use(player, boots(charge), false, 3, false, -1);
                var result = use(player, mixed(charge, false, false), true, 3, false, -1);
                require(original.equals(result) && result.energy == charge && result.processed == (charge == COST ? 3 : 0), "Exempt admission/payment differs");
            }
            record(mode + ": payment exemption without lowering base-budget eligibility");
        }
        player.setGameMode(GameType.SURVIVAL);
        var root = mixed(150_000, false, true); var before = root.copy();
        var result = use(player, root, true, 3, false, -1);
        require(result.processed == 3 && result.energy == 0, "Two Eclipse halves activated/billed twice"); unchangedHistory(before, root);
        record("two Eclipse sources: one action/pass, one live wallet, no doubled provider execution");
        var filteringNative = use(player, boots(250_000), false, 0, true, -1);
        var filteringMixed = use(player, mixed(250_000, false, false), true, 0, true, -1);
        require(filteringNative.equals(filteringMixed) && filteringMixed.processed == 3 && filteringMixed.affected == 2 && filteringMixed.energy == 100_000,
                "Ground/AABB/tag/multipart/immune filtering or billing differs: " + filteringNative + " / " + filteringMixed);
        record("actual grounded/AABB-edge/airborne/outside/warden-tag/multipart controls; immune target still processed and billed, addEffect false does not refund");
        for (String control : List.of("earthquake_upgrade_installed", "earthquake_toggle", "tool_enabled")) {
            root = mixed(150_000, false, false); JustDireAttributeTestSupport.set(root, control, false); before = root.copy();
            require(use(player, root, true, 1, false, -1).processed == 0 && ItemStack.matches(before, root), "Disabled root control mutated state");
        }
        var config = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var enabled = (ModConfigSpec.BooleanValue) config.get(earthquake); boolean previous = enabled.get();
        try {
            enabled.set(false); root = mixed(COST, false, false); before = root.copy();
            require(use(player, root, true, 1, false, -1).processed == 0 && ItemStack.matches(before, root), "Native disabled config authorized mixed action");
        } finally { enabled.set(previous); }
        var unsupported = NeoForgeJustDireResourcesTest.boots("celestigem", 0);
        JustDireAttributeTestSupport.set(unsupported, "earthquake_upgrade_installed", true);
        require(!(boolean) nativeUsable.invoke(unsupported.getItem(), unsupported, earthquake), "Celestigem unexpectedly supports Earthquake independently of charge");
        root = MixedBootsFactory.createPair(unsupported, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", true); before = root.copy();
        require(use(player, root, true, 1, false, -1).processed == 0 && ItemStack.matches(before, root), "Forged flag granted unsupported tier");
        root = mixed(COST, false, false); JustDireAttributeTestSupport.set(root, "earthquake_value", -999);
        require(use(player, root, true, 1, false, -1).processed == 1, "Unused native numeric setting became an eligibility requirement");
        record("root switches/installation and native config veto; forged Celestigem denied; native fixed numeric setting not read by effect");
        root = mixed(150_000, false, false); before = root.copy();
        result = use(player, root, true, 3, false, 1);
        require(result.processed == 1 && result.energy == 100_000 && result.timer.equals(new Timer(ACTIVE, true)), "Unexpected short receipt gave a free effect or rolled back prior targets");
        unchangedHistory(before, root);
        record("candidate fault policy: real staged partial extraction rolled back for second target before effect; first target/bill and active lockout retained");
        root = mixed(COST, false, false); before = root.copy(); player.setItemSlot(EquipmentSlot.FEET, root);
        require(!(boolean) nativeAction.invoke(null, player.level(), player, root) && ItemStack.matches(before, root), "Native action unexpectedly supported mixed identity");
        JustDireAttributeTestSupport.set(root, "earthquake_bindingmode", 2);
        var bindingClass = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(bindingClass.getConstructor(String.class, int.class, boolean.class, boolean.class)
                .newInstance("earthquake", 71, false, true)));
        before = root.copy();
        require(!JustDireSettingsCompatibility.inputBinding(root, 71, false)
                && !JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false)).accepted()
                && ItemStack.matches(before, root), "Spike enabled Earthquake production input");
        record("direct native action cannot execute mixed identity; production Earthquake-only binding/prepare still reject read-only");
        root = mixed(COST, false, false); player.setItemSlot(EquipmentSlot.FEET, root);
        JustDireAttributeTestSupport.set(root, "groundstomp_upgrade_installed", true);
        JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(bindingClass.getConstructor(String.class, int.class, boolean.class, boolean.class)
                .newInstance("groundstomp", 71, false, true)));
        root.set(timers(), List.of(timerRecord(77, false)));
        var prepared = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(2, 71, false));
        require(prepared.accepted(), "Unrelated Earthquake cooldown prevented initial Ground Stomp quote");
        candidateTick(player, root); before = root.copy();
        require(!JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(2, prepared.nonce(), 71, false))
                && ItemStack.matches(before, root), "Existing paid snapshot tolerated an unrelated ticking clock unexpectedly");
        record("integration limit: valid production Ground Stomp paid permit is rejected after only the unrelated Earthquake timer ticks; independent action-clock snapshots need a production decision/proof");
        clockBoundaries(player);
    }

    private static Result use(ServerPlayer player, ItemStack stack, boolean mixed, int count, boolean filtering, int faultAt) throws Exception {
        player.setItemSlot(EquipmentSlot.FEET, stack);
        var targets = new ArrayList<Mob>();
        try {
            for (int index = 0; index < count; index++) { targets.add(target(player, index + 1, true, false, false, false)); }
            if (filtering) {
                targets.add(target(player, 2, true, false, false, false));
                targets.add(target(player, 5.1, true, false, false, false)); // Intersecting edge, center outside five blocks.
                targets.add(target(player, 2, false, false, false, false));
                targets.add(target(player, 6, true, false, false, false));
                targets.add(target(player, 2, true, true, false, false));
                targets.add(target(player, 2, true, false, true, false));
                targets.add(target(player, 2, true, false, false, true));
            }
            if (mixed) { candidate(player, stack, faultAt); }
            else { require(!(boolean) nativeAction.invoke(null, player.level(), player, stack), "Native return changed"); }
            int affected = 0;
            for (var mob : targets) {
                var effect = mob.getEffect(MobEffects.SLOWNESS);
                if (effect != null) { require(effect.getAmplifier() == 3 && effect.getDuration() == 200, "Wrong native-equivalent effect"); affected++; }
            }
            // Processed count is determined from effects plus the deliberately immune target.
            int processed = affected + (filtering && timer(stack) != null && charge(stack) < 250_000 ? 1 : 0);
            return new Result(charge(stack), affected, processed, timer(stack));
        } finally { targets.forEach(Mob::discard); }
    }

    private static boolean usable(ItemStack root) throws Exception {
        if (root.getCount() != 1 || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("earthquake_upgrade_installed"), false)
                || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("earthquake_toggle"), true)
                || !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("tool_enabled"), true)) { return false; }
        var data = ModContent.getMixedBootsData(root).orElse(null); if (data == null) { return false; }
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (!source.is(boots(0).getItem())) { continue; }
            JustDireAttributeTestSupport.set(source, "earthquake_upgrade_installed", true);
            JustDireAttributeTestSupport.set(source, "earthquake_toggle", true);
            JustDireAttributeTestSupport.set(source, "tool_enabled", true);
            if ((boolean) nativeUsable.invoke(source.getItem(), source, earthquake)) { return charge(root) >= COST; }
        }
        return false;
    }

    private static void candidate(ServerPlayer player, ItemStack root, int faultAt) throws Exception {
        if (player.getItemBySlot(EquipmentSlot.FEET) != root || timer(root) != null || !usable(root)) { return; }
        var records = new ArrayList<>(root.getOrDefault(timers(), List.of())); records.add(timerRecord(ACTIVE, true)); root.set(timers(), records);
        int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING));
        int bill = player.gameMode() == GameType.CREATIVE || player.gameMode() == GameType.SPECTATOR ? 0
                : (int) Math.max(0, COST - COST * Math.min(1, unbreaking * .1));
        int index = 0;
        var bounds = new AABB(player.getX() - 5, player.getY() - 5, player.getZ() - 5, player.getX() + 5, player.getY() + 5, player.getZ() + 5);
        for (var mob : player.level().getEntitiesOfClass(Mob.class, bounds)) {
            if (!(boolean) nativeFilter.invoke(null, mob)) { continue; }
            if (player.getItemBySlot(EquipmentSlot.FEET) != root || !usable(root)) { continue; }
            // Candidate fault policy differs from native effect-first order: commit this target's
            // debit before its effect, retaining prior targets and lockout on a short receipt.
            if (bill > 0) {
                try (var transaction = Transaction.openRoot()) {
                    int requested = index == faultAt ? bill - 1 : bill;
                    if (JustDireResourceTestSupport.handler(root).extract(requested, transaction) != bill) { break; }
                    transaction.commit();
                }
            }
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3), mob); index++;
        }
    }

    private static void clockBoundaries(ServerPlayer player) throws Exception {
        var original = boots(0); original.set(timers(), List.of(timerRecord(1, true))); nativeTick.invoke(null, player.level(), original, player);
        require(timer(original).equals(new Timer(COOLDOWN, false)), "Native active expiry differs");
        var root = mixed(0, false, false); root.set(timers(), List.of(timerRecord(1, true))); player.setItemSlot(EquipmentSlot.FEET, root);
        nativeTick.invoke(null, player.level(), root, player);
        require(timer(root).equals(new Timer(1, true)), "Mixed native delegation limit changed");
        var unrelated = recordConstructor.newInstance("groundstomp", 77, false);
        root.set(timers(), List.of(unrelated, timerRecord(1, true))); candidateTick(player, root);
        require(timer(root).equals(new Timer(COOLDOWN, false)) && root.get(timers()).getFirst().equals(unrelated), "Owned transition touched unrelated timer");
        root.set(timers(), List.of(unrelated, timerRecord(1, false))); candidateTick(player, root);
        require(timer(root) == null && root.get(timers()).equals(List.of(unrelated)), "Owned expiry discarded unrelated timer");
        for (boolean active : new boolean[]{false, true}) {
            root.set(timers(), List.of(timerRecord(0, active))); var before = root.copy(); candidateTick(player, root);
            require(ItemStack.matches(before, root), "Candidate revived zero unsupported record");
        }
        original.set(timers(), List.of(timerRecord(0, true))); nativeTick.invoke(null, player.level(), original, player);
        require(timer(original).equals(new Timer(-1, true)), "Native zero record did not become unsupported negative");
        record("native active 1 -> 800 inactive; naive native mixed tick stuck at active 1; owned expiry preserves Ground Stomp; zero/negative records need retain/deny policy");
    }

    private static void startClock(MinecraftServer server) throws Exception {
        nativePlayer = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        mixedPlayer = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        nativeClock = boots(COST); mixedClock = mixed(COST, true, false);
        nativePlayer.setItemSlot(EquipmentSlot.FEET, nativeClock); mixedPlayer.setItemSlot(EquipmentSlot.FEET, mixedClock);
        nativeAction.invoke(null, nativePlayer.level(), nativePlayer, nativeClock);
        candidate(mixedPlayer, mixedClock, -1);
        require(timer(nativeClock).equals(new Timer(ACTIVE, true)) && timer(mixedClock).equals(new Timer(ACTIVE, true))
                && charge(nativeClock) == COST && charge(mixedClock) == COST, "Natural empty-target activation failed");
    }

    private static void naturalClock() throws Exception {
        if (clock == 150 && paused < 10) {
            nativePlayer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); mixedPlayer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            var nativeBefore = nativeClock.copy(); var mixedBefore = mixedClock.copy();
            nativeClock.getItem().inventoryTick(nativeClock, nativePlayer.level(), nativePlayer, EquipmentSlot.MAINHAND);
            candidateTick(mixedPlayer, mixedClock);
            require(ItemStack.matches(nativeBefore, nativeClock) && ItemStack.matches(mixedBefore, mixedClock), "Unequipped natural clock advanced");
            paused++; return;
        }
        nativePlayer.setItemSlot(EquipmentSlot.FEET, nativeClock); mixedPlayer.setItemSlot(EquipmentSlot.FEET, mixedClock);
        if (lateTarget != null) { lateTarget.setOnGround(true); }
        nativeClock.getItem().inventoryTick(nativeClock, nativePlayer.level(), nativePlayer, EquipmentSlot.FEET);
        candidateTick(mixedPlayer, mixedClock); clock++;
        require(java.util.Objects.equals(timer(nativeClock), timer(mixedClock)), "Natural clock differs at " + clock);
        require(charge(nativeClock) == (clock <= 40 ? COST : 0) && charge(mixedClock) == charge(nativeClock), "Natural ticking spent FE at " + clock);
        if (lateTarget != null) { require(lateTarget.getEffect(MobEffects.SLOWNESS) == null, "Active period repeated the effect on a late target"); }
        if (clock == 20) { lateTarget = target(nativePlayer, 2, true, false, false, false); }
        if (clock == 40) {
            lateTarget.discard(); lateTarget = null;
            for (var root : List.of(nativeClock, mixedClock)) {
                try (var transaction = Transaction.openRoot()) {
                    require(JustDireResourceTestSupport.handler(root).extract(COST, transaction) == COST, "Clock fixture depletion failed"); transaction.commit();
                }
                for (String name : List.of("tool_enabled", "earthquake_toggle", "earthquake_upgrade_installed")) { JustDireAttributeTestSupport.set(root, name, false); }
            }
        }
        if (clock == 100 || clock == 400) {
            nativeClock = roundTrip(nativePlayer, nativeClock); mixedClock = roundTrip(mixedPlayer, mixedClock);
        }
        if (clock == ACTIVE) { require(timer(mixedClock).equals(new Timer(COOLDOWN, false)), "Natural phase transition reset/missed"); }
        if (clock == ACTIVE + COOLDOWN) { require(timer(mixedClock) == null, "Natural full lockout did not expire"); }
    }

    private static void candidateTick(ServerPlayer player, ItemStack root) throws Exception {
        if (player.getItemBySlot(EquipmentSlot.FEET) != root || ModContent.getMixedBootsData(root).isEmpty()) { return; }
        var records = new ArrayList<>();
        for (Object record : root.getOrDefault(timers(), List.of())) {
            if (!recordName.invoke(record).equals("earthquake") || (int) recordTicks.invoke(record) <= 0) { records.add(record); continue; }
            int remaining = (int) recordTicks.invoke(record); boolean active = (boolean) recordActive.invoke(record);
            if (remaining > 1) { records.add(timerRecord(remaining - 1, active)); }
            else if (active) { records.add(timerRecord(COOLDOWN, false)); }
        }
        root.set(timers(), records);
    }

    private static ItemStack roundTrip(ServerPlayer player, ItemStack root) {
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        require(ItemStack.matches(root, loaded), "Active/cooldown persistence changed full root"); return loaded;
    }

    private static Mob target(ServerPlayer player, double distance, boolean grounded, boolean tagged, boolean multipart, boolean immune) throws Exception {
        Mob mob = multipart ? new MultipartMob(player.level()) : immune ? new ImmuneMob(player.level())
                : (tagged ? EntityType.WARDEN : EntityType.ZOMBIE).create(player.level(), EntitySpawnReason.COMMAND);
        require(mob != null, "No world target"); mob.setNoAi(true); mob.setNoGravity(true);
        mob.setPos(player.getX() + distance, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob), "Target not inserted");
        mob.setOnGround(grounded); // Position/spawn initialization can reset the controlled initial contact flag.
        require(mob.onGround() == grounded, "Fixture contact flag did not stick");
        require((boolean) nativeFilter.invoke(null, mob) == (grounded && !tagged && !multipart), "Unexpected native target predicate: " + mob.getType());
        require(player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob), "Target not visible in actual world query at " + mob.position());
        return mob;
    }
    private static ItemStack boots(int charge) {
        var root = NeoForgeJustDireResourcesTest.boots("eclipsealloy", charge);
        JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", true); return root;
    }
    private static ItemStack mixed(int charge, boolean inverse, boolean twoEclipse) {
        var pair = MixedBootsFactory.createPair(boots(0), twoEclipse ? boots(0) : new ItemStack(Items.DIAMOND_BOOTS));
        var root = inverse ? pair.inverse() : pair.primary();
        try (var transaction = Transaction.openRoot()) {
            require(JustDireResourceTestSupport.handler(root).insert(charge, transaction) == charge, "Live root recharge failed"); transaction.commit();
        }
        return root;
    }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static DataComponentType<List<Object>> timers() { return JustDireAttributeTestSupport.component("ability_cooldowns"); }
    private static Object timerRecord(int ticks, boolean active) throws Exception { return recordConstructor.newInstance("earthquake", ticks, active); }
    private static Timer timer(ItemStack root) throws Exception {
        for (Object record : root.getOrDefault(timers(), List.of())) {
            if (recordName.invoke(record).equals("earthquake")) { return new Timer((int) recordTicks.invoke(record), (boolean) recordActive.invoke(record)); }
        }
        return null;
    }
    private static void unchangedHistory(ItemStack before, ItemStack after) {
        require(ModContent.getMixedBootsData(before).equals(ModContent.getMixedBootsData(after)) && before.getDamageValue() == after.getDamageValue(), "Action changed source history or root wear");
    }
    private static void record(String message) { groups++; report.append(message).append('\n'); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private record Timer(int ticks, boolean active) {}
    private record Result(int energy, int affected, int processed, Timer timer) {}
    private static final class MultipartMob extends Zombie {
        MultipartMob(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override public boolean isMultipartEntity() { return true; }
        @Override public PartEntity<?>[] getParts() { return new PartEntity<?>[0]; }
    }
    private static final class ImmuneMob extends Zombie {
        ImmuneMob(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override public boolean canBeAffected(MobEffectInstance effect) { return !effect.getEffect().equals(MobEffects.SLOWNESS) && super.canBeAffected(effect); }
    }
}
