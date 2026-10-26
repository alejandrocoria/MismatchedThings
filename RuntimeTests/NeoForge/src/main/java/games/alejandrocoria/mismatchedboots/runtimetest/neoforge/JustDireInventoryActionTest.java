package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAbilityTarget;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveState;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Actual internal production actions and callbacks, without predicate overrides. */
final class JustDireInventoryActionTest {
    private static final int KEY = 72;
    private static Method stomp;
    private static Method earthquake;
    private static Method passives;
    private static Method stompTick;

    private JustDireInventoryActionTest() {
    }

    static List<String> exercise(MinecraftServer server) throws Exception {
        JustDireEarthquakeTestSupport.bind();
        stomp = method(JustDireGroundStompCompatibility.class, "activate", JustDireAbilityTarget.class, ItemStack.class, int.class, boolean.class);
        earthquake = method(JustDireEarthquakeCompatibility.class,
                            "activate",
                            JustDireAbilityTarget.class,
                            ItemStack.class,
                            int.class,
                            boolean.class);
        passives = method(JustDireSettingsCompatibility.class, "toggleBoundPassives", JustDireAbilityTarget.class, int.class, boolean.class);
        stompTick = method(JustDireGroundStompCompatibility.class, "tickEquipped", ServerPlayer.class);
        var records = new ArrayList<String>();
        actions(server);
        records.add(
                "production location actions: both orientations in hotbar/main/FEET/offhand, native GS/EQ effects and 55000 FE; per-ability location/device filters and unchanged sources");
        invalidation(server);
        records.add(
                "production locations: moved/equal-replaced/unsupported/native/invalid-count targets refused; GS extraction move/replacement rolls back FE; EQ replacement stops debit/effect after admitted lockout");
        passiveAndClocks(server);
        records.add(
                "production location passives: Step/Jump/Lava/Negate toggle free disabled/depleted; actual GS/EQ inventory pause and FEET-only resume");
        breaks(server);
        records.add(
                "production location break: inventory/hotbar/offhand/FEET stats and native slot callbacks, once-only credits, preserved unrelated armor and actual Soul Speed effect; refund-time replacement never receives break cleanup");
        batch(server);
        records.add(
                "production batch: ordered hotbar/main/FEET/offhand shared GS/EQ/passive actions, independent 55000 FE and source preservation; depleted peer does not suppress funded root");
        batchInvalidation(server);
        records.add(
                "production batch attenuation: server move/equal-copy/charge/control mutations skip one target only; equipment-event invalidation is monotonic; client subset excludes discarded slots and cannot add new targets");
        batchForgery(server);
        records.add(
                "production batch permission: empty/duplicate/unsupported/unknown/oversized subsets, wrong key/device/nonce, replay, expiry and GUI refusal; newly ready action excluded and reentrant dispatch refused");
        return records;
    }

    private static ItemStack place(ServerPlayer player, int slot, int amount, List<String> names) throws Exception {
        var root = root(amount, false);
        configure(root, false, false, names);
        player.getInventory().setItem(slot, root);
        return root;
    }

    private static JustDireActiveNetwork.Activate packet(JustDireActiveNetwork.ActivationReply reply, List<Integer> slots) {
        return new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), KEY, false, slots);
    }

    private static void batch(MinecraftServer server) throws Exception {
        var player = player(server);
        var roots = new ArrayList<ItemStack>();
        var slots = List.of(0, 9, 36, 40);
        for (int slot : slots) {
            roots.add(place(player, slot, 100_000, List.of("groundstomp", "earthquake", "stepheight")));
        }

        var history = roots.stream().map(ModContent::getMixedBootsData).toList();
        var victim = mob(player);
        try {
            var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
            require(reply.accepted() && JustDireActiveNetwork.activate(player, packet(reply, List.of(40, 36, 9, 0))), "Shared batch refused");
            for (int index = 0; index < roots.size(); index++) {
                var root = roots.get(index);
                require(charge(root) == 45_000 && JustDireGroundStompTestSupport.timer(root) > 0
                                && JustDireEarthquakeTestSupport.clock(root, "earthquake") != null
                                && !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true)
                                && history.get(index).equals(ModContent.getMixedBootsData(root)), "Independent batch root not committed once");
            }
            require(victim.hasEffect(MobEffects.SLOWNESS), "Batch missing real EQ world effect");
            require(!JustDireActiveNetwork.activate(player, packet(reply, slots)), "Batch replay accepted");
        } finally {
            victim.discard();
        }
        player = player(server);
        var depleted = place(player, 0, 0, List.of("earthquake", "stepheight"));
        var funded = place(player, 9, 100_000, List.of("groundstomp"));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        require(JustDireActiveNetwork.activate(player, packet(reply, List.of(0, 9))) && charge(funded) == 95_000
                        && charge(depleted) == 0 && !depleted.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"),
                                                                           true),
                "Depleted peer blocked free/funded actions");
    }

    private static void batchInvalidation(MinecraftServer server) throws Exception {
        for (int fault = 0; fault < 5; fault++) {
            var player = player(server);
            var first = place(player, 9, 100_000, List.of("groundstomp"));
            var second = place(player, 36, 100_000, List.of("groundstomp"));
            var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
            if (fault == 0) {
                player.getInventory().setItem(9, ItemStack.EMPTY);
                player.getInventory().setItem(10, first);
            }
            if (fault == 1) {
                player.getInventory().setItem(9, first.copy());
            }
            if (fault == 2) {
                set(first, "forge_energy", 101_000);
            }
            if (fault == 3) {
                set(first, "groundstomp_value", 5);
            }
            if (fault == 4) {
                player.setItemSlot(EquipmentSlot.FEET, second.copy());
                NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(player, EquipmentSlot.FEET, second, player.getItemBySlot(EquipmentSlot.FEET)));
                player.setItemSlot(EquipmentSlot.FEET, second);
            }
            require(JustDireActiveNetwork.activate(player, packet(reply, List.of(9, 36))), "One stale target cancelled whole batch");
            require(fault == 4 ? charge(first) == 95_000 && charge(second) == 100_000
                               : charge(first) == (fault == 2 ? 101_000 : 100_000) && charge(second) == 95_000,
                    "Stale target followed/revived or funded peer skipped");
        }

        var player = player(server);
        var excluded = place(player, 9, 100_000, List.of("groundstomp"));
        var retained = place(player, 40, 100_000, List.of("groundstomp"));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        require(JustDireActiveNetwork.activate(player, packet(reply, List.of(40))) && charge(excluded) == 100_000 && charge(retained) == 95_000,
                "Client attenuation authorized discarded root");
        player = player(server);
        var earlier = place(player, 0, 100_000, List.of("groundstomp"));
        var later = place(player, 9, 100_000, List.of("groundstomp"));
        var orderedPlayer = player;
        reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        JustDireGroundStompServerGuardPlugin.debitMutation = () -> orderedPlayer.getInventory().setItem(9, later.copy());
        try {
            require(JustDireActiveNetwork.activate(player, packet(reply, List.of(9, 0))) && charge(earlier) == 95_000 && charge(later) == 100_000,
                    "Client order reordered authority or inter-target callback was ignored");
        } finally {
            JustDireGroundStompServerGuardPlugin.debitMutation = null;
        }
        player = player(server);
        var paid = place(player, 9, 100_000, List.of("groundstomp"));
        set(paid, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("earthquake", 5, true)));
        reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        set(paid, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("earthquake", 4, true)));
        require(!JustDireActiveNetwork.activate(player, packet(reply, List.of(9))) && charge(paid) == 100_000,
                "Paused inventory paid target received worn peer-clock tolerance");
        player = player(server);
        var free = place(player, 9, 0, List.of("stepheight"));
        set(free, "tool_enabled", false);
        reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        set(free, "forge_energy", 1_000);
        free.setDamageValue(1);
        set(free, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("earthquake", 4, true)));
        require(JustDireActiveNetwork.activate(player, packet(reply, List.of(9))) && charge(free) == 1_000
                        && !free.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true),
                "Ordinary inventory charge/wear/clock changes cancelled free switch");
    }

    private static void batchForgery(MinecraftServer server) throws Exception {
        for (var slots : List.of(List.<Integer>of(), List.of(9, 9), List.of(37), List.of(0), java.util.Collections.nCopies(42, 9))) {
            var player = player(server);
            var root = place(player, 9, 100_000, List.of("groundstomp"));
            var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
            if (slots.equals(List.of(0))) {
                place(player, 0, 100_000, List.of("groundstomp"));
            }
            require(!JustDireActiveNetwork.activate(player, packet(reply, slots)) && charge(root) == 100_000
                            && !JustDireActiveNetwork.activate(player, packet(reply, List.of(9))), "Forged subset accepted or nonce survived");
        }
        for (int fault = 0; fault < 6; fault++) {
            var player = player(server);
            var root = place(player, 9, 100_000, List.of("groundstomp"));
            var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
            var packet = new JustDireActiveNetwork.Activate(reply.sequence(), fault == 2 ? java.util.UUID.randomUUID() : reply.nonce(),
                                                            fault == 0 ? KEY + 1 : KEY, fault == 1, List.of(9));
            if (fault == 3) {
                server.getWorldData().overworldData().setGameTime(player.level().getGameTime() + 41);
            }
            if (fault == 4) {
                player.containerMenu = new net.minecraft.world.inventory.HopperMenu(91, player.getInventory());
            }
            if (fault == 5) {
                player.setServerLevel(server.getLevel(net.minecraft.world.level.Level.NETHER));
            }
            require(!JustDireActiveNetwork.activate(player, packet) && charge(root) == 100_000, "Invalid batch context accepted");
        }

        var player = player(server);
        var root = place(player, 9, 100_000, List.of("groundstomp", "stepheight"));
        set(root, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("groundstomp", 1, false)));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        set(root, "ability_cooldowns", List.of());
        require(JustDireActiveNetwork.activate(player, packet(reply, List.of(9))) && charge(root) == 100_000
                        && !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true),
                "Newly ready paid action joined free permit");
        player = player(server);
        root = place(player, 9, 100_000, List.of("groundstomp"));
        reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false));
        var currentPlayer = player;
        var currentPacket = packet(reply, List.of(9));
        JustDireGroundStompServerGuardPlugin.debitMutation = () -> {
            require(!JustDireActiveNetwork.activate(currentPlayer, currentPacket)
                            && !JustDireActiveNetwork.prepare(currentPlayer, new JustDireActiveNetwork.Prepare(2, KEY, false)).accepted(),
                    "Reentrant batch admitted");
        };
        try {
            require(JustDireActiveNetwork.activate(player, currentPacket) && charge(root) == 95_000, "Outer batch failed after reentry refusal");
        } finally {
            JustDireGroundStompServerGuardPlugin.debitMutation = null;
        }
    }

    private static void actions(MinecraftServer server) throws Exception {
        for (boolean inverse : new boolean[]{false, true}) {
            for (int slot : new int[]{0, 9, JustDireAbilityTarget.FEET_SLOT, 40}) {
                var player = player(server);
                var root = root(100_000, inverse);
                configure(root, false, false, List.of("groundstomp", "earthquake"));
                player.getInventory().setItem(slot, root);
                var target = new JustDireAbilityTarget(player, slot, root);
                var history = ModContent.getMixedBootsData(root);
                var mob = mob(player);
                try {
                    var expected = JustDireActiveState.snapshot(root);
                    require(use(stomp, target, expected) && use(earthquake, target, expected), "Internal location action rejected");
                    require(charge(root) == 45_000 && mob.hasEffect(MobEffects.SLOWNESS) && mob.getDeltaMovement().horizontalDistance() > 0
                                    && history.equals(ModContent.getMixedBootsData(root)), "Location effects/resources/sources differ");
                } finally {
                    mob.discard();
                }
                configure(root, true, false, List.of("groundstomp"));
                require(JustDireSettingsCompatibility.groundStompBinding(target, KEY, false) == (slot == JustDireAbilityTarget.FEET_SLOT),
                        "Held/inventory treated as equipped");
                configure(root, false, true, List.of("groundstomp"));
                require(JustDireSettingsCompatibility.groundStompBinding(target, 4, true)
                                && !JustDireSettingsCompatibility.groundStompBinding(target, 4, false), "Mouse binding location/device mismatch");
            }
        }
    }

    private static void invalidation(MinecraftServer server) throws Exception {
        var player = player(server);
        var root = root(100_000, false);
        configure(root, false, false, List.of("groundstomp", "earthquake"));
        player.getInventory().setItem(9, root);
        var target = new JustDireAbilityTarget(player, 9, root);
        var expected = JustDireActiveState.snapshot(root);
        player.getInventory().setItem(9, ItemStack.EMPTY);
        player.getInventory().setItem(10, root);
        require(!use(stomp, target, expected) && !use(earthquake, target, expected) && charge(root) == 100_000, "Moved root followed");
        player.getInventory().setItem(9, root.copy());
        require(!use(stomp, target, expected), "Equal replacement accepted");
        for (int slot : new int[]{-1, 37, 38, 39, 41, 100}) {
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                player.getInventory().setItem(slot, root);
            }
            var unsupported = new JustDireAbilityTarget(player, slot, root);
            require(!unsupported.valid() && !use(stomp, unsupported, expected), "Unsupported slot accepted");
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.getInventory().setItem(9, JustDireEarthquakeTestSupport.original(100_000));
        require(!new JustDireAbilityTarget(player, 9, player.getInventory().getItem(9)).valid(), "Native item accepted as mixed");
        player.getInventory().setItem(9, root);
        root.setCount(2);
        require(!target.valid() && !use(stomp, target, JustDireActiveState.snapshot(root)), "Invalid count accepted");
        root.setCount(1);
        for (Method action : List.of(stomp, earthquake)) {
            for (boolean move : new boolean[]{false, true}) {
                var paid = root(100_000, false);
                configure(paid, false, false, List.of("groundstomp", "earthquake"));
                player.getInventory().setItem(9, paid);
                var current = new JustDireAbilityTarget(player, 9, paid);
                var victim = mob(player);
                JustDireGroundStompServerGuardPlugin.debitMutation = () -> {
                    player.getInventory().setItem(9, move ? ItemStack.EMPTY : paid.copy());
                    if (move) {
                        player.getInventory().setItem(10, paid);
                    }
                };
                try {
                    boolean accepted = use(action, current, JustDireActiveState.snapshot(paid));
                    require(accepted == (action == earthquake) && JustDireGroundStompServerGuardPlugin.debitMutation == null
                                    && charge(paid) == 100_000 && !victim.hasEffect(MobEffects.SLOWNESS)
                                    && victim.getDeltaMovement().horizontalDistance() == 0, "Location extraction rollback/effect mismatch");
                    require(move ? player.getInventory().getItem(10) == paid : charge(player.getInventory().getItem(9)) == 100_000,
                            "Debit affected the moved/replacement root");
                    require(action == earthquake ? JustDireEarthquakeTestSupport.clock(paid, "earthquake") != null
                                                 : JustDireGroundStompTestSupport.timer(paid) == -1, "Lockout commit semantics changed");
                } finally {
                    JustDireGroundStompServerGuardPlugin.debitMutation = null;
                    victim.discard();
                }
            }
        }
        player.getInventory().setItem(9, root);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        require(JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false)).accepted(),
                "Inventory wire entry unavailable");
    }

    private static void passiveAndClocks(MinecraftServer server) throws Exception {
        var player = player(server);
        var root = root(0, false);
        var names = List.of("stepheight", "jumpboost", "lavarepair", "negatefalldamage");
        configure(root, false, false, names);
        set(root, "tool_enabled", false);
        player.getInventory().setItem(9, root);
        var target = new JustDireAbilityTarget(player, 9, root);
        var before = root.copy();
        require(JustDireSettingsCompatibility.boundPassives(target, KEY, false).equals(names) && (boolean) passives.invoke(null, target, KEY, false),
                "Free inventory passive dispatch rejected");
        for (String name : names) {
            set(before, name + "_toggle", false);
        }
        require(ItemStack.matches(root, before), "Inventory passives changed values/resources/sources");
        set(root,
            "ability_cooldowns",
            List.of(JustDireEarthquakeTestSupport.timer("groundstomp", 2, false), JustDireEarthquakeTestSupport.timer("earthquake", 1, true)));
        before = root.copy();
        stompTick.invoke(null, player);
        JustDireEarthquakeTestSupport.tick(player);
        require(ItemStack.matches(root, before), "Off-slot timers advanced");
        player.getInventory().setItem(9, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, root);
        stompTick.invoke(null, player);
        JustDireEarthquakeTestSupport.tick(player);
        require(JustDireGroundStompTestSupport.timer(root) == 1
                        && JustDireEarthquakeTestSupport.clock(root, "earthquake").equals(new JustDireEarthquakeTestSupport.Clock(800, false)),
                "Worn-only clocks failed to resume");
    }

    private static void breaks(MinecraftServer server) throws Exception {
        JustDireRefundBreakTest.register();
        try {
            for (int slot : new int[]{9, 0, 40, JustDireAbilityTarget.FEET_SLOT}) {
                var player = new NeoForgeJustDireGroundStompServerTest.BreakObserver(server);
                var root = JustDireRefundBreakTest.mixed("blazegold", "groundstomp");
                configure(root, false, false, List.of("groundstomp"));
                root.setDamageValue(root.getMaxDamage() - 25);
                var item = root.getItem();
                JustDireRefundBreakTest.observe(player, root);
                var worn = new ItemStack(Items.NETHERITE_BOOTS);
                worn.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SOUL_SPEED), 3);
                player.setItemSlot(EquipmentSlot.FEET, worn);
                player.getInventory().setItem(slot, root);
                if (slot != JustDireAbilityTarget.FEET_SLOT) {
                    player.level().setBlock(player.blockPosition().below(), Blocks.SOUL_SAND.defaultBlockState(), 3);
                    player.setOnGround(true);
                    worn.forEachModifier(EquipmentSlot.FEET,
                                         (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
                    EnchantmentHelper.runLocationChangedEffects(player.level(), worn, player, EquipmentSlot.FEET);
                    require(!player.activeLocationDependentEnchantments(EquipmentSlot.FEET).isEmpty(), "Soul Speed witness did not activate");
                }

                double armor = player.getAttributeValue(Attributes.ARMOR);
                double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                var equipment = slot == 9 ? null : slot == 0 ? EquipmentSlot.MAINHAND : slot == 40 ? EquipmentSlot.OFFHAND : EquipmentSlot.FEET;
                require(use(stomp, new JustDireAbilityTarget(player, slot, root), JustDireActiveState.snapshot(root)) && root.isEmpty()
                                && player.callbacks == (equipment == null ? 0 : 1) && player.brokenSlot == equipment
                                && player.getStats().getValue(Stats.ITEM_BROKEN.get(item)) == 1, "Break location/feedback/stat mismatch");
                JustDireRefundBreakTest.refunds("groundstomp");
                if (slot != JustDireAbilityTarget.FEET_SLOT) {
                    require(player.getItemBySlot(EquipmentSlot.FEET) == worn && player.getAttributeValue(Attributes.ARMOR) == armor
                                    && player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed
                                    && !player.activeLocationDependentEnchantments(EquipmentSlot.FEET).isEmpty(),
                            "Inventory/hand break cleaned up unrelated footwear");
                }
            }
            // Refund emission occurs after shrink but before onBreak: replace the slot at that exact callback boundary.
            var player = new NeoForgeJustDireGroundStompServerTest.BreakObserver(server);
            var root = JustDireRefundBreakTest.mixed("blazegold", "groundstomp");
            configure(root, false, false, List.of("groundstomp"));
            var item = root.getItem();
            root.setDamageValue(root.getMaxDamage() - 25);
            player.setItemSlot(EquipmentSlot.FEET, root);
            JustDireRefundBreakTest.observe(player, root);
            var replacement = new ItemStack(Items.NETHERITE_BOOTS);
            JustDireRefundBreakTest.onRefund(() -> {
                player.setItemSlot(EquipmentSlot.FEET, replacement);
                replacement.forEachModifier(EquipmentSlot.FEET,
                                            (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
            });
            require(use(stomp, new JustDireAbilityTarget(player, JustDireAbilityTarget.FEET_SLOT, root), JustDireActiveState.snapshot(root))
                            && root.isEmpty() && player.callbacks == 0 && player.getStats().getValue(Stats.ITEM_BROKEN.get(item)) == 1
                            && player.getAttributeValue(Attributes.ARMOR) == 3
                            && player.getItemBySlot(EquipmentSlot.FEET) == replacement, "Refund-time replacement received break cleanup");
            JustDireRefundBreakTest.refunds("groundstomp");
        } finally {
            JustDireRefundBreakTest.clearObservation();
        }
    }

    static ItemStack root(int amount, boolean inverse) {
        var pair = MixedBootsFactory.createPair(JustDireEarthquakeTestSupport.original(0), JustDireGroundStompTestSupport.boots("blazegold", 0));
        var root = inverse ? pair.inverse() : pair.primary();
        set(root, "forge_energy", amount);
        return root;
    }

    static void configure(ItemStack root, boolean equipped, boolean mouse, List<String> names) throws Exception {
        configure(root, equipped, mouse, names, KEY);
    }

    static void configure(ItemStack root, boolean equipped, boolean mouse, List<String> names, int key) throws Exception {
        var constructor = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                               .getConstructor(String.class, int.class, boolean.class, boolean.class);
        var records = new ArrayList<Object>();
        for (String name : names) {
            if (!name.equals("lavarepair")) {
                set(root, name + "_upgrade_installed", true);
            }
            set(root, name + "_toggle", true);
            set(root, name + "_bindingmode", 2);
            records.add(constructor.newInstance(name, mouse ? 4 : key, mouse, equipped));
        }
        set(root, "ability_bindings", List.copyOf(records));
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        var result = type.getDeclaredMethod(name, args);
        result.setAccessible(true);
        return result;
    }

    private static boolean use(Method action, JustDireAbilityTarget target, ItemStack expected) throws Exception {
        return (boolean) action.invoke(null, target, expected, KEY, false);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
    }

    private static Mob mob(ServerPlayer player) {
        var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
        require(mob != null, "No action target");
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPos(player.getX() + 1, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob), "Target not inserted");
        mob.setOnGround(true);
        return mob;
    }

    private static int charge(ItemStack root) {
        return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
    }

    private static <T> void set(ItemStack root, String name, T value) {
        JustDireAttributeTestSupport.set(root, name, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
