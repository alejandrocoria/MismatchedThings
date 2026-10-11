package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Disposable server action prototype, also reused by the input proof; no production hook or accepted provider policy. */
final class NeoForgeJustDireGroundStompActionSpike {
    private static final String PREFIX = "just-dire-ground-stomp-action-spike";
    private static final Set<ServerPlayer> executing = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer lifecyclePlayer;
    private static ItemStack lifecycleRoot;
    private static ItemStack lifecycleBefore;
    private static int elapsed;
    private static int groups;
    private static boolean finished;

    private NeoForgeJustDireGroundStompActionSpike() {}

    static void start(MinecraftServer server) throws ReflectiveOperationException {
        effectsAndPolicies(server);
        rejectionAndFailure(server);
        enchantmentsAndBreak(server);
        lifecyclePlayer = NeoForgeJustDireGroundStompSpike.player(server, GameType.SURVIVAL);
        lifecycleRoot = root("eclipsealloy", 20_000);
        lifecyclePlayer.setItemSlot(EquipmentSlot.FEET, lifecycleRoot);
        require(activate(lifecyclePlayer, lifecycleRoot, lifecycleRoot.copy(), 71, false, false, Fault.NONE), "Lifecycle activation rejected");
        lifecycleBefore = lifecycleRoot.copy();
    }

    static void tick(MinecraftServer server) {
        if (finished || lifecycleRoot == null) { return; }
        try {
            // This is the opt-in prototype's real server-tick listener, not production inventoryTick.
            if (elapsed == 10) { lifecyclePlayer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); }
            if (elapsed == 16) {
                require(timer(lifecycleRoot) == 190, "Unequipped cooldown advanced during six server ticks");
                lifecyclePlayer.setItemSlot(EquipmentSlot.FEET, lifecycleRoot);
                set(lifecycleRoot, "groundstomp_upgrade_installed", false);
            }
            tickRoot(lifecyclePlayer, lifecycleRoot);
            elapsed++;
            if (elapsed < 206) { return; }
            require(timer(lifecycleRoot) == -1 && charge(lifecycleRoot) == 5_000, "Equipped expiry or single initial bill failed");
            set(lifecycleRoot, "groundstomp_upgrade_installed", true);
            require(activate(lifecyclePlayer, lifecycleRoot, lifecycleRoot.copy(), 71, false, false, Fault.NONE)
                    && charge(lifecycleRoot) == 0 && timer(lifecycleRoot) == 200, "Re-enabled expired root did not pay once");
            require(ModContent.getMixedBootsData(lifecycleBefore).equals(ModContent.getMixedBootsData(lifecycleRoot)), "Timer lifecycle mutated provenance");
            record("206 normal server ticks: 200 worn + six unequipped; disabled installation still counts down; re-enable does not reset; next activation pays once; immutable sources unchanged");
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups
                    + " action spike groups; test-only mixed server effects/payment/timer; no mixed client protocol/UI or restart acceptance\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            abort(server);
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); abort(server); }
    }

    static void abort(MinecraftServer server) {
        finished = true;
        server.overworld().setChunkForced(0, 0, false);
        server.halt(false);
    }

    private static void effectsAndPolicies(MinecraftServer server) throws ReflectiveOperationException {
        var player = NeoForgeJustDireGroundStompSpike.player(server, GameType.SURVIVAL);
        double[] distances = {2, -2, 0, 3.01, 4};
        var nativeResult = NeoForgeJustDireGroundStompSpike.nativeUse(player, NeoForgeJustDireGroundStompSpike.boots("eclipsealloy", 6_000), distances);
        var pair = MixedBootsFactory.createPair(NeoForgeJustDireGroundStompSpike.boots("blazegold", 0),
                NeoForgeJustDireGroundStompSpike.boots("eclipsealloy", 12_000));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            configure(root);
            var before = root.copy();
            var result = mixedUse(player, root, false, distances);
            require(result.equals(nativeResult.motions()) && charge(root) == 1_000 && timer(root) == 200
                    && root.getDamageValue() == before.getDamageValue(), "Mixed native target/motion/single FE bill mismatch");
            require(ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(before)), "Action mutated sources");
            var after = root.copy();
            require(mixedUse(player, root, false, new double[]{2}).getFirst() == 0 && ItemStack.matches(root, after), "Duplicate activation changed cooldown/root/target");
            record("both orientations: native-equivalent airborne/multiple/coincident/AABB knockback; one 5000 FE bill, root timer 200; duplicate unchanged; wear and sources unchanged");
        }
        var depleted = root("eclipsealloy", 20_000);
        require(mixedUse(player, depleted, false, new double[]{}).isEmpty() && charge(depleted) == 5_000 && timer(depleted) == 200,
                "Empty target list skipped bill/cooldown");
        record("empty targets still cost/cool down once");
        for (boolean fixed : new boolean[]{false, true}) {
            var coexist = MixedBootsFactory.createPair(NeoForgeJustDireGroundStompSpike.boots("blazegold", 0),
                    NeoForgeJustDireGroundStompSpike.boots("eclipsealloy", 2_000)).primary();
            configure(coexist);
            var before = coexist.copy();
            double motion = mixedUse(player, coexist, fixed, new double[]{2}).getFirst();
            require(fixed ? motion == 0 && ItemStack.matches(coexist, before)
                    : motion > 0 && coexist.getDamageValue() == before.getDamageValue() + 25 && charge(coexist) == 1_000 && timer(coexist) == 200,
                    "Candidate selection policy did not control single bill/effect");
        }
        record("depleted powered + Blazegold: strongest-usable prototype pays 25 durability once; fixed highest-tier rejects unchanged; no policy selected");
    }

    private static void rejectionAndFailure(MinecraftServer server) throws ReflectiveOperationException {
        var player = NeoForgeJustDireGroundStompSpike.player(server, GameType.SURVIVAL);
        for (String control : List.of("tool_enabled", "groundstomp_toggle", "groundstomp_upgrade_installed")) {
            var root = root("eclipsealloy", 12_000); set(root, control, false);
            requireRejected(player, root, root.copy(), 71, false, Fault.NONE);
        }
        var root = root("eclipsealloy", 12_000);
        var masquerading = root.transmuteCopy(Items.DIAMOND_BOOTS, 1);
        requireRejected(player, masquerading, masquerading.copy(), 71, false, Fault.NONE);
        requireRejected(player, root, root.copy(), 72, false, Fault.NONE);
        requireRejected(player, root, root.copy(), 71, true, Fault.NONE);
        var stale = root.copy(); set(root, "groundstomp_value", 1);
        requireRejected(player, root, stale, 71, false, Fault.NONE);
        set(root, "groundstomp_bindingmode", 99);
        requireRejected(player, root, root.copy(), 71, false, Fault.NONE);
        var ineligible = root("ferricore", 0);
        requireRejected(player, ineligible, ineligible.copy(), 71, false, Fault.NONE);
        Map<?, ?> config = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) config.get(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability").getField("GROUNDSTOMP").get(null));
        boolean previous = flag.get();
        try { flag.set(false); var disabled = root("eclipsealloy", 12_000); requireRejected(player, disabled, disabled.copy(), 71, false, Fault.NONE); }
        finally { flag.set(previous); }
        record("disabled root installation/controls/config, non-mixed item carrying root components, wrong key/device, stale component snapshot, malformed mode and unsupported tier reject before effect/payment/cooldown");
        for (Fault fault : List.of(Fault.QUERY_UNAVAILABLE, Fault.PARTIAL_DEBIT, Fault.REPLACED_DURING_DEBIT)) {
            var failed = root("eclipsealloy", 12_000);
            requireRejected(player, failed, failed.copy(), 71, false, fault);
            require(charge(player.getItemBySlot(EquipmentSlot.FEET)) == 6_000, "Failed debit charged replacement stack");
        }
        record("explicit query-unavailable seam and actual incomplete/replaced-root transaction extraction: rollback, no effect/timer/fallback retry");
        var replaced = root("eclipsealloy", 12_000);
        player.setItemSlot(EquipmentSlot.FEET, replaced.copy());
        require(!activate(player, replaced, replaced.copy(), 71, false, false, Fault.NONE), "Detached server identity authorized action");
        record("detached expected server root rejected, including equal-component replacement; not a wire target token proof");
    }

    private static void enchantmentsAndBreak(MinecraftServer server) throws ReflectiveOperationException {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR)) {
            var player = NeoForgeJustDireGroundStompSpike.player(server, mode);
            for (int level : new int[]{0, 3, 10}) {
                var root = root("eclipsealloy", 12_000);
                if (level != 0) { root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level); }
                root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
                int bill = mode == GameType.SURVIVAL ? (int) (5_000 * (1 - Math.min(1, level * .1))) : 0;
                require(mixedUse(player, root, false, new double[]{2}).getFirst() > 0 && charge(root) == 6_000 - bill
                        && timer(root) == 200 && player.getItemBySlot(EquipmentSlot.FEET) == root, "Root enchantment/mode payment mismatch");
                var insufficient = root("eclipsealloy", 9_998);
                insufficient.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
                requireRejected(player, insufficient, insufficient.copy(), 71, false, Fault.NONE);
            }
        }
        record("root FE Unbreaking 0/3/10 and creative/spectator: full base eligibility retained, discounted/exempt single bill; Binding Curse without slot exchange");
        var player = NeoForgeJustDireGroundStompSpike.player(server, GameType.SURVIVAL);
        var original = NeoForgeJustDireGroundStompSpike.boots("blazegold", 0);
        var mixed = root("blazegold", 0);
        var enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        original.enchant(enchantment, 3); mixed.enchant(enchantment, 3);
        var ordinaryRoot = mixed.copy();
        player.setItemSlot(EquipmentSlot.FEET, ordinaryRoot);
        server.overworld().getRandom().setSeed(123456);
        ordinaryRoot.hurtAndBreak(25, player, EquipmentSlot.FEET);
        server.overworld().getRandom().setSeed(123456);
        NeoForgeJustDireGroundStompSpike.nativeUse(player, original, new double[]{});
        server.overworld().getRandom().setSeed(123456);
        mixedUse(player, mixed, false, new double[]{});
        require(ordinaryRoot.getDamageValue() == mixed.getDamageValue() && mixed.getDamageValue() > 0 && mixed.getDamageValue() < 25,
                "Ability bill differs from actual ordinary root Unbreaking wear");
        require(original.is(ItemTags.ARMOR_ENCHANTABLE) && mixed.is(ItemTags.ARMOR_ENCHANTABLE)
                && original.getDamageValue() == mixed.getDamageValue(), "Native and mixed armor Unbreaking wear diverged");
        record("seeded Unbreaking III: native Blazegold wear=" + original.getDamageValue() + ", mixed ability/ordinary root wear=" + mixed.getDamageValue()
                + "; real root armor tag preserves native armor Unbreaking behavior");
        var observer = new BreakObserver(server);
        var breaking = root("blazegold", 0); breaking.setDamageValue(breaking.getMaxDamage() - 25);
        observer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        var replacement = root("eclipsealloy", 12_000);
        observer.callback = () -> {
            observer.setItemSlot(EquipmentSlot.FEET, replacement);
            try { require(!activate(observer, replacement, replacement.copy(), 71, false, false, Fault.NONE), "Break callback re-entered another equipped root action"); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        };
        require(mixedUse(observer, breaking, false, new double[]{2}).getFirst() > 0 && breaking.isEmpty()
                && observer.callbacks == 1 && observer.brokenSlot == EquipmentSlot.FEET && observer.getMainHandItem().is(Items.STONE)
                && charge(replacement) == 6_000 && timer(replacement) == -1, "Actual break callback/slot/reentrancy failure");
        record("exact 25 durability break: actual FEET callback once; hand unchanged; equipped replacement during callback cannot re-enter; paid action still produces one effect");
        var faultRoot = root("eclipsealloy", 12_000); observer.setItemSlot(EquipmentSlot.FEET, faultRoot);
        try {
            activate(observer, faultRoot, faultRoot.copy(), 71, false, false, Fault.AFTER_PAYMENT);
            throw new AssertionError("Post-payment failure was swallowed");
        } catch (PostPaymentFailure expected) {
            require(charge(faultRoot) == 1_000 && timer(faultRoot) == 200
                    && !activate(observer, faultRoot, faultRoot.copy(), 71, false, false, Fault.NONE), "Post-payment failure refunded or permitted retry");
        }
        record("synthetic failure after successful bill/timer propagates; no refund or retry; irreversible boundary is not ACID");
        var historical = root("blazegold", 0);
        var history = cooldownRecord("groundstomp", 10, false);
        var unrelated = cooldownRecord("earthquake", 1, true);
        // Exercise the real native append on a shallow copy, then isolate the prototype's writes.
        var nativeAlias = NeoForgeJustDireGroundStompSpike.boots("blazegold", 0);
        nativeAlias.set(cooldowns(), new ArrayList<>(List.of(unrelated)));
        var abilityType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool")
                .getMethod("addCooldown", ItemStack.class, abilityType, int.class, boolean.class)
                .invoke(null, nativeAlias.copy(), abilityType.getField("GROUNDSTOMP").get(null), 200, false);
        require(nativeAlias.get(cooldowns()).size() == 2, "Native shallow-copy cooldown alias behavior changed; recheck write seam");
        var shared = new ArrayList<>(List.of(history, unrelated));
        historical.set(cooldowns(), shared);
        var copied = historical.copy(); player.setItemSlot(EquipmentSlot.FEET, copied);
        tickRoot(player, copied);
        require(timer(historical) == 10 && timer(copied) == 9 && shared.equals(List.of(history, unrelated))
                && copied.get(cooldowns()).contains(unrelated), "Copy-on-write timer changed shared history or unsupported active timer");
        var sources = ModContent.getMixedBootsData(copied).orElseThrow();
        var recomposed = MixedBootsRecomposer.recompose(copied, sources.left().source(), sources.right().source());
        require(recomposed.get(cooldowns()).equals(copied.get(cooldowns())), "Live timer reset across production preservation/recomposition");
        record("native addCooldown on a shallow copy mutates original list; prototype copy-on-write countdown isolates history, keeps unsupported active Earthquake untouched; test-only preservation keeps live timer through recomposition, not actual smithing/restart");
    }

    /** Test-only ordering candidate: preflight -> exact FE bill -> cooldown -> wear -> effects, no post-commit catch. */
    static boolean activate(ServerPlayer player, ItemStack root, ItemStack expected, int key) throws ReflectiveOperationException {
        // The connection proof has one eligible powered provider; the two candidate policies agree.
        return activate(player, root, expected, key, false, false, Fault.NONE);
    }

    private static boolean activate(ServerPlayer player, ItemStack root, ItemStack expected, int key, boolean mouse, boolean fixed, Fault fault)
            throws ReflectiveOperationException {
        if (!executing.add(player)) { return false; }
        try {
            if (fault == Fault.QUERY_UNAVAILABLE || !equipped(player, root) || !ItemStack.matches(root, expected)
                    || ModContent.getMixedBootsData(root).isEmpty() || !NeoForgeJustDireGroundStompSpike.bindingMatches(root, player, key, mouse)
                    || timer(root) != -1) { return false; }
            var quote = NeoForgeJustDireGroundStompSpike.quote(root, fixed);
            if (quote == null) { return false; }
            int particles = root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_custom_setting"), 0);
            if (particles != 0 && particles != 1) { return false; }
            // Materialize optional data and world query before entering the irreversible boundary.
            // Native addCooldown mutates shared lists, even through stack copies. Keep snapshots isolated.
            var nextTimers = new ArrayList<>(root.getOrDefault(cooldowns(), List.of()));
            nextTimers.add(cooldownRecord("groundstomp", 200, false));
            var targets = player.level().getEntitiesOfClass(Mob.class,
                    new AABB(player.getX() - 3, player.getY() - 3, player.getZ() - 3, player.getX() + 3, player.getY() + 3, player.getZ() + 3),
                    NeoForgeJustDireGroundStompActionSpike::validTarget);
            int level = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING));
            var mode = player.gameMode.getGameModeForPlayer();
            boolean exempt = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
            int bill = exempt ? 0 : quote.powered() ? (int) Math.max(0, quote.bill() - quote.bill() * Math.min(1, level * .1)) : quote.bill();
            if (!equipped(player, root) || !ItemStack.matches(root, expected)) { return false; }
            if (quote.powered() && bill > 0) {
                var handler = JustDireResourceTestSupport.handler(root);
                if (handler == null) { return false; }
                try (var transaction = Transaction.openRoot()) {
                    int extracted = handler.extract(fault == Fault.PARTIAL_DEBIT ? 7_000 : bill, transaction);
                    if (fault == Fault.REPLACED_DURING_DEBIT) { player.setItemSlot(EquipmentSlot.FEET, expected.copy()); }
                    if (extracted != (fault == Fault.PARTIAL_DEBIT ? 7_000 : bill) || !equipped(player, root)) { return false; }
                    transaction.commit();
                }
            }
            root.set(cooldowns(), List.copyOf(nextTimers));
            if (!quote.powered() && bill > 0) { root.hurtAndBreak(bill, player, EquipmentSlot.FEET); }
            if (fault == Fault.AFTER_PAYMENT) { throw new PostPaymentFailure(); }
            for (var mob : targets) {
                double x = mob.getX() - player.getX(), z = mob.getZ() - player.getZ();
                double length = Math.sqrt(x * x + z * z);
                if (length != 0) { mob.knockback(quote.value(), -x / length, -z / length); }
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, .5f, 1);
            if (particles == 0) { player.level().sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY(), player.getZ(), 20, .5, .2, .5, 0); }
            return true;
        } finally { executing.remove(player); }
    }

    static void tickRoot(ServerPlayer player, ItemStack root) throws ReflectiveOperationException {
        if (!equipped(player, root)) { return; }
        var next = new ArrayList<Object>();
        boolean changed = false;
        for (Object record : root.getOrDefault(cooldowns(), List.of())) {
            var type = record.getClass();
            if (!type.getMethod("abilityName").invoke(record).equals("groundstomp") || (boolean) type.getMethod("isactive").invoke(record)) { next.add(record); continue; }
            int ticks = (int) type.getMethod("cooldownTicks").invoke(record);
            changed = true;
            if (ticks > 1) { next.add(cooldownRecord("groundstomp", ticks - 1, false)); }
        }
        if (changed) { root.set(cooldowns(), List.copyOf(next)); }
    }

    private static List<Double> mixedUse(ServerPlayer player, ItemStack root, boolean fixed, double[] distances) throws ReflectiveOperationException {
        player.setItemSlot(EquipmentSlot.FEET, root);
        var targets = new ArrayList<Mob>();
        try {
            for (double distance : distances) {
                var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
                require(mob != null, "Missing effect target");
                mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(player.getX() + distance, player.getY(), player.getZ());
                mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                require(player.level().addFreshEntity(mob) && player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob), "Target not visible to real world query");
                targets.add(mob);
            }
            activate(player, root, root.copy(), 71, false, fixed, Fault.NONE);
            return targets.stream().map(mob -> mob.getDeltaMovement().horizontalDistance()).toList();
        } finally { targets.forEach(Mob::discard); }
    }

    private static void requireRejected(ServerPlayer player, ItemStack root, ItemStack expected, int key, boolean mouse, Fault fault)
            throws ReflectiveOperationException {
        player.setItemSlot(EquipmentSlot.FEET, root);
        var before = root.copy();
        var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
        require(mob != null, "Missing rejection target");
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(player.getX() + 2, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob) && player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob), "Rejection target not visible");
        try {
            require(!activate(player, root, expected, key, mouse, false, fault) && ItemStack.matches(before, root)
                    && mob.getDeltaMovement().horizontalDistance() == 0, "Rejected request mutated root/effect: " + fault);
        } finally { mob.discard(); }
    }

    private static ItemStack root(String tier, int sourceCharge) throws ReflectiveOperationException {
        var root = MixedBootsFactory.createPair(NeoForgeJustDireGroundStompSpike.boots(tier, sourceCharge), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        configure(root); return root;
    }

    private static void configure(ItemStack root) throws ReflectiveOperationException {
        set(root, "groundstomp_upgrade_installed", true);
        set(root, "groundstomp_bindingmode", 2);
        var type = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
        set(root, "ability_bindings", List.of(type.getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true)));
    }

    private static Object cooldownRecord(String name, int ticks, boolean active) throws ReflectiveOperationException {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                .getConstructor(String.class, int.class, boolean.class).newInstance(name, ticks, active);
    }

    private static DataComponentType<List<Object>> cooldowns() { return JustDireAttributeTestSupport.component("ability_cooldowns"); }
    // Native filtering is Entity-typed even though the world query selects Mob instances.
    private static boolean validTarget(Entity entity) { return !entity.isMultipartEntity() && !(entity instanceof PartEntity<?>); }
    private static boolean equipped(ServerPlayer player, ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && root.is(ModContent.MIXED_BOOTS_KEY)
                && root.getItem() instanceof MixedBootsItem && player.getItemBySlot(EquipmentSlot.FEET) == root;
    }
    private static int timer(ItemStack root) throws ReflectiveOperationException { return NeoForgeJustDireGroundStompSpike.timer(root); }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static <T> void set(ItemStack stack, String path, T value) { JustDireAttributeTestSupport.set(stack, path, value); }
    private static void record(String text) { groups++; report.append(text).append('\n'); }
    private static void require(boolean condition, String text) { if (!condition) { throw new AssertionError(text); } }
    private enum Fault { NONE, QUERY_UNAVAILABLE, PARTIAL_DEBIT, REPLACED_DURING_DEBIT, AFTER_PAYMENT }
    private static final class PostPaymentFailure extends RuntimeException {}

    private static final class BreakObserver extends ServerPlayer {
        private int callbacks;
        private EquipmentSlot brokenSlot;
        private Runnable callback;

        private BreakObserver(MinecraftServer server) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), "stomp-break"), ClientInformation.createDefault());
            new ServerGamePacketListenerImpl(server, new net.minecraft.network.Connection(PacketFlow.SERVERBOUND), this, CommonListenerCookie.createInitial(getGameProfile(), false));
            connection.markClientLoaded(); setGameMode(GameType.SURVIVAL); setPos(8, 100, 8); setOnGround(false);
        }

        @Override public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
            callbacks++; brokenSlot = slot;
            super.onEquippedItemBroken(item, slot);
            if (callback != null) { callback.run(); }
        }
    }
}
