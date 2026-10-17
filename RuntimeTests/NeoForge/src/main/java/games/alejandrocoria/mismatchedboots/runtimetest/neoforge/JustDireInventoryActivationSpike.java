package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAbilityTarget;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveState;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Disposable in-process batch/location prototype, NOT a production packet, UI or replacement action implementation. */
public final class JustDireInventoryActivationSpike {
    private static final String PREFIX = "just-dire-inventory-spike";
    private static final int KEY = 72;
    private static final StringBuilder report = new StringBuilder();
    private static Batch pending;
    private static int warmup, groups;
    private static boolean finished;
    private static Method nativeUse, nativeBindings, stomp, earthquake, passives, stompTick;

    private JustDireInventoryActivationSpike() {}

    static void register() { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer())); }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        if (++warmup == 1) { server.overworld().setChunkForced(0, 0, true); }
        if (warmup < 200 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) { return; }
        finished = true;
        try {
            require(warmup < 200, "Entity chunk never became ticking");
            JustDireGroundStompTestSupport.initializeApi(); JustDireEarthquakeTestSupport.bind();
            Class<?> tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            nativeUse = tool.getMethod("useAbility", Level.class, Player.class, ItemStack.class, int.class, boolean.class);
            nativeBindings = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.LeftClickableTool")
                    .getMethod("getCustomBindingListFor", ItemStack.class, int.class, boolean.class, Player.class);
            stomp = action(JustDireGroundStompCompatibility.class, "activate", JustDireAbilityTarget.class, ItemStack.class, int.class, boolean.class);
            earthquake = action(JustDireEarthquakeCompatibility.class, "activate", JustDireAbilityTarget.class, ItemStack.class, int.class, boolean.class);
            passives = action(JustDireSettingsCompatibility.class, "toggleBoundPassives", JustDireAbilityTarget.class, int.class, boolean.class);
            stompTick = action(JustDireGroundStompCompatibility.class, "tickEquipped", ServerPlayer.class);
            nativeContract(server); mixedBatch(server); staleTargets(server); clocks(server); durability(server);
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " bounded inventory research groups\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
        finally { pending = null; server.overworld().setChunkForced(0, 0, false); server.halt(false); }
    }

    private static <T> T within(Target target, Function<JustDireAbilityTarget, T> operation) {
        return operation.apply(new JustDireAbilityTarget(target.player, target.slot, target.root));
    }

    /** One server-derived batch avoids competing per-slot Prepare requests replacing a single player permit. */
    private static Batch prepare(ServerPlayer player, int key, boolean mouse) {
        var targets = new ArrayList<Target>();
        if (player.containerMenu == player.inventoryMenu) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack root = player.getInventory().getItem(slot);
                if (!JustDireSettingsCompatibility.mixed(root)) { continue; }
                var target = new Target(player, slot, root, JustDireActiveState.snapshot(root), 0);
                Target eligible = within(target, location -> {
                    int actions = 0;
                    if (JustDireSettingsCompatibility.groundStompBinding(location, key, mouse) && JustDireGroundStompCompatibility.quote(root) != null) { actions |= 1; }
                    if (JustDireSettingsCompatibility.earthquakeBinding(location, key, mouse) && JustDireEarthquakeCompatibility.quote(root) != null) { actions |= 2; }
                    return actions == 0 && JustDireSettingsCompatibility.boundPassives(location, key, mouse).isEmpty() ? null
                            : new Target(player, target.slot, root, actions == 0 ? JustDireActiveNetwork.passiveInputSnapshot(root) : target.snapshot, actions);
                });
                if (eligible != null) { targets.add(eligible); }
            }
        }
        pending = new Batch(player, key, mouse, player.level().getGameTime(), List.copyOf(targets));
        return pending;
    }

    private static int activate(Batch batch) {
        if (batch != pending) { return 0; }
        pending = null; // Consume even an invalidated batch; a stale target cannot be retried at its new slot.
        int accepted = 0;
        for (Target target : batch.targets) {
            boolean result = within(target, location -> {
                if (!location.valid() || batch.player.level().getGameTime() > batch.issued + 40) { return false; }
                boolean same = target.actions == 0 ? ItemStack.matches(JustDireActiveNetwork.passiveInputSnapshot(target.root), target.snapshot)
                        : JustDireActiveState.paidMatches(target.root, target.snapshot, target.actions, batch.player.level().getGameTime() - batch.issued + 1);
                if (!same) { return false; }
                var expected = JustDireActiveState.snapshot(target.root);
                boolean acted = false;
                // Current native boot order; production already owns and validates its action ordering separately.
                if ((target.actions & 1) != 0) { acted = invoke(stomp, location, expected, batch.key, batch.mouse); }
                if (location.valid() && ItemStack.matches(target.root, expected) && (target.actions & 2) != 0) {
                    acted |= invoke(earthquake, location, expected, batch.key, batch.mouse);
                }
                if (location.valid() && ItemStack.matches(target.root, expected)) {
                    acted |= invoke(passives, location, batch.key, batch.mouse);
                }
                return acted;
            });
            if (result) { accepted++; }
        }
        return accepted;
    }

    private static void nativeContract(MinecraftServer server) throws Exception {
        var player = player(server);
        for (boolean mouse : new boolean[]{false, true}) {
            int key = mouse ? 4 : KEY;
            for (int slot : new int[]{0, 9, 35, 36, 40}) {
                for (boolean requireEquipped : new boolean[]{false, true}) {
                    player.getInventory().clearContent();
                    var root = configure(JustDireGroundStompTestSupport.boots("eclipsealloy", 100_000), key, mouse, requireEquipped, List.of("groundstomp", "stepheight"));
                    player.getInventory().setItem(slot, root);
                    boolean eligible = !((List<?>) nativeBindings.invoke(null, root, key, mouse, player)).isEmpty();
                    require(eligible == (!requireEquipped || slot == 36), "Native per-binding location filter changed");
                }
            }
        }
        player.getInventory().clearContent();
        var root = configure(JustDireEarthquakeTestSupport.original(100_000), KEY, false, false, List.of("groundstomp", "earthquake", "stepheight"));
        player.getInventory().setItem(9, root); var mob = target(player);
        try {
            nativeUse.invoke(root.getItem(), player.level(), player, root, KEY, false);
            require(charge(root) == 45_000 && mob.hasEffect(MobEffects.SLOWNESS) && mob.getDeltaMovement().horizontalDistance() > 0
                    && !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true), "Native inventory dispatch: FE=" + charge(root)
                    + " slow=" + mob.hasEffect(MobEffects.SLOWNESS) + " motion=" + mob.getDeltaMovement() + " step=" + root.get(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle")));
        } finally { mob.discard(); }
        record("native: per-binding equipped/inventory filter at hotbar/main/feet/offhand for key and mouse; actual inventory use runs both active effects, bills 55000 FE and toggles Step");
    }

    private static void mixedBatch(MinecraftServer server) throws Exception {
        for (boolean mouse : new boolean[]{false, true}) {
            var player = player(server); int key = mouse ? 4 : KEY;
            var roots = new ArrayList<ItemStack>();
            for (int slot : new int[]{0, 9, 36, 40}) {
                var root = root(100_000, key, mouse, false, List.of("groundstomp", "earthquake", "stepheight"));
                player.getInventory().setItem(slot, root); roots.add(root);
            }
            var excluded = root(100_000, key, mouse, true, List.of("groundstomp")); player.getInventory().setItem(10, excluded);
            var nativeRoot = configure(JustDireGroundStompTestSupport.boots("eclipsealloy", 100_000), key, mouse, false, List.of("groundstomp"));
            player.getInventory().setItem(11, nativeRoot);
            var provenance = roots.stream().map(stack -> games.alejandrocoria.mismatchedboots.content.ModContent.getMixedBootsData(stack).orElseThrow()).toList();
            var batch = prepare(player, key, mouse); require(batch.targets.size() == 4, "Batch failed to collect all and only eligible mixed slots");
            var mob = target(player);
            try {
                require(activate(batch) == 4 && activate(batch) == 0, "Batch lost candidates or replayed");
                for (int i = 0; i < roots.size(); i++) {
                    var stack = roots.get(i);
                    require(charge(stack) == 45_000 && JustDireEarthquakeTestSupport.clock(stack, "earthquake").active()
                            && JustDireGroundStompTestSupport.timer(stack) == 200 && !stack.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true)
                            && provenance.get(i).equals(games.alejandrocoria.mismatchedboots.content.ModContent.getMixedBootsData(stack).orElseThrow()), "Root action/resource/provenance mismatch");
                }
                require(mob.hasEffect(MobEffects.SLOWNESS) && mob.getDeltaMovement().horizontalDistance() > 0
                        && charge(excluded) == 100_000 && charge(nativeRoot) == 100_000, "World effects missing or excluded/native root changed");
            } finally { mob.discard(); }
        }
        var player = player(server);
        var split = root(10_000, KEY, false, true, List.of("groundstomp", "stepheight"));
        var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class);
        set(split, "ability_bindings", List.of(binding.newInstance("groundstomp", KEY, false, true), binding.newInstance("stepheight", KEY, false, false)));
        player.getInventory().setItem(9, split);
        require(activate(prepare(player, KEY, false)) == 1 && charge(split) == 10_000
                && JustDireGroundStompTestSupport.timer(split) == -1, "Per-ability location choice leaked to equipped-only action");
        player.getInventory().setItem(9, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, split);
        require(activate(prepare(player, KEY, false)) == 1 && charge(split) == 5_000, "Equipping did not admit the equipped-only action");
        record("candidate: one consumed batch dispatches four mixed pairs (hotbar/main/feet/offhand) for keyboard and mouse; actual production shared actions/passive switches, per-root FE and unchanged sources; equipped-only inventory and native objects excluded");
        report.append("per-ability flags: inventory Step binding does not admit equipped-only Ground Stomp; equipping admits both\n");
    }

    private static void staleTargets(MinecraftServer server) throws Exception {
        var player = player(server);
        var moved = root(20_000, KEY, false, false, List.of("groundstomp")); player.getInventory().setItem(9, moved);
        var stable = root(20_000, KEY, false, false, List.of("groundstomp")); player.getInventory().setItem(10, stable);
        var batch = prepare(player, KEY, false);
        player.getInventory().setItem(9, ItemStack.EMPTY); player.getInventory().setItem(11, moved);
        require(activate(batch) == 1 && charge(moved) == 20_000 && charge(stable) == 15_000, "Moved target authorized or suppressed independent target");
        player.getInventory().clearContent(); player.getInventory().setItem(9, moved);
        batch = prepare(player, KEY, false); var equal = moved.copy(); player.getInventory().setItem(9, equal);
        require(activate(batch) == 0 && charge(equal) == 20_000 && charge(moved) == 20_000, "Equal replacement inherited permission");
        batch = prepare(player, KEY, false); set(equal, "forge_energy", 21_000);
        require(activate(batch) == 0, "Paid-resource change inherited permission");
        batch = prepare(player, KEY, false);
        JustDireGroundStompServerGuardPlugin.debitMutation = () -> player.getInventory().setItem(9, equal.copy());
        require(activate(batch) == 0 && JustDireGroundStompServerGuardPlugin.debitMutation == null && charge(equal) == 21_000
                && charge(player.getInventory().getItem(9)) == 21_000 && JustDireGroundStompTestSupport.timer(equal) == -1,
                "Replacement during actual staged extraction did not roll back payment");
        // Existing production permits still reject inventory; only this candidate dispatches inventory targets.
        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false)).accepted(), "Production widened outside the candidate");
        var disabled = root(0, KEY, false, false, List.of("stepheight")); set(disabled, "tool_enabled", false);
        player.getInventory().setItem(12, disabled);
        require(activate(prepare(player, KEY, false)) >= 1 && !disabled.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true), "Disabled/depleted inventory passive binding blocked");
        record("candidate: moved/equal-replaced/charged roots denied; replacement during actual staged extraction rolls back FE/no timer; unrelated target still runs; batch cannot replay; free passive toggle works disabled/depleted; ordinary production remains equipped-only");
    }

    private static void clocks(MinecraftServer server) throws Exception {
        var player = player(server); var root = root(10_000, KEY, false, false, List.of("groundstomp"));
        var original = configure(JustDireGroundStompTestSupport.boots("eclipsealloy", 10_000), KEY, false, false, List.of("groundstomp"));
        player.getInventory().setItem(9, root); player.getInventory().setItem(10, original);
        require(activate(prepare(player, KEY, false)) == 1, "Inventory clock setup failed");
        nativeUse.invoke(original.getItem(), player.level(), player, original, KEY, false);
        for (int i = 0; i < 5; i++) { stompTick.invoke(null, player); original.getItem().inventoryTick(original, player.level(), player, null); }
        require(JustDireGroundStompTestSupport.timer(root) == 200 && JustDireGroundStompTestSupport.timer(original) == 200, "Off-slot clock advanced");
        player.getInventory().setItem(9, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, root); stompTick.invoke(null, player);
        require(JustDireGroundStompTestSupport.timer(root) == 199, "Equipped mixed clock did not resume");
        player.setItemSlot(EquipmentSlot.FEET, original); original.getItem().inventoryTick(original, player.level(), player, EquipmentSlot.FEET);
        require(JustDireGroundStompTestSupport.timer(original) == 199, "Native equipped clock did not resume");
        var active = root(0, KEY, false, false, List.of("earthquake"));
        set(active, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("earthquake", 1, true)));
        player.getInventory().setItem(9, active); var before = active.copy(); JustDireEarthquakeTestSupport.tick(player);
        require(ItemStack.matches(before, active), "Inventory Earthquake active phase advanced");
        player.getInventory().setItem(9, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, active); JustDireEarthquakeTestSupport.tick(player);
        require(JustDireEarthquakeTestSupport.clock(active, "earthquake").equals(new JustDireEarthquakeTestSupport.Clock(800, false)), "Earthquake active phase failed to resume");
        record("native/production clocks: Ground Stomp inventory pause and worn resume; mixed Earthquake active-phase pause and resume into native 800-tick cooldown; no widened inventory ticking");
    }

    private static void durability(MinecraftServer server) throws Exception {
        var player = player(server); var equipped = new ItemStack(Items.NETHERITE_BOOTS); player.setItemSlot(EquipmentSlot.FEET, equipped);
        var beforeFeet = equipped.copy();
        {
            equipped.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
            double armor = player.getAttributeValue(Attributes.ARMOR);
            var root = configure(MixedBootsFactory.createPair(JustDireGroundStompTestSupport.boots("blazegold", 0), new ItemStack(Items.DIAMOND_BOOTS)).primary(),
                    KEY, false, false, List.of("groundstomp"));
            root.setDamageValue(root.getMaxDamage() - 25); player.getInventory().setItem(9, root);
            require(activate(prepare(player, KEY, false)) == 1 && root.isEmpty(), "Inventory durability action did not break the paid root");
            require(player.getItemBySlot(EquipmentSlot.FEET) == equipped && ItemStack.matches(equipped, beforeFeet), "Inventory break changed unrelated footwear");
            double afterArmor = player.getAttributeValue(Attributes.ARMOR);
            require(afterArmor == armor, "Production break callback affected worn armor");
            report.append("OBSERVATION: production callback")
                    .append(" armor ").append(armor).append(" -> ").append(afterArmor).append('\n');
        }
        record("production callback preserves unrelated armor and breaks only the inventory root; original failing callback is retired");
    }

    private static Method action(Class<?> type, String name, Class<?>... args) throws Exception { var method = type.getDeclaredMethod(name, args); method.setAccessible(true); return method; }
    private static boolean invoke(Method method, Object... args) {
        try { return (boolean) method.invoke(null, args); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static ServerPlayer player(MinecraftServer server) { return JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL); }
    private static Mob target(ServerPlayer player) {
        var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND); require(mob != null, "No target");
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(player.getX() + 1, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob), "Target not inserted"); mob.setOnGround(true); return mob;
    }
    private static ItemStack root(int charge, int key, boolean mouse, boolean requireEquipped, List<String> names) throws Exception {
        var source = JustDireEarthquakeTestSupport.original(0); set(source, "groundstomp_upgrade_installed", true);
        var root = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        set(root, "forge_energy", charge); return configure(root, key, mouse, requireEquipped, names);
    }
    private static ItemStack configure(ItemStack stack, int key, boolean mouse, boolean requireEquipped, List<String> names) throws Exception {
        var constructor = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class);
        var records = new ArrayList<Object>();
        for (String name : names) {
            set(stack, name + "_upgrade_installed", true); set(stack, name + "_toggle", true); set(stack, name + "_bindingmode", 2);
            records.add(constructor.newInstance(name, key, mouse, requireEquipped));
        }
        set(stack, "ability_bindings", List.copyOf(records)); return stack;
    }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static <T> void set(ItemStack root, String name, T value) { JustDireAttributeTestSupport.set(root, name, value); }
    private static void record(String message) { groups++; report.append(message).append('\n'); }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
    private record Target(ServerPlayer player, int slot, ItemStack root, ItemStack snapshot, int actions) {}
    private record Batch(ServerPlayer player, int key, boolean mouse, long issued, List<Target> targets) {}
}
