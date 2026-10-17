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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Actual internal production actions and callbacks. No inventory wire/UI support or predicate overrides. */
final class JustDireInventoryActionTest {
    private static final int KEY = 72;
    private static Method stomp, earthquake, passives, stompTick;

    private JustDireInventoryActionTest() {}

    static List<String> exercise(MinecraftServer server) throws Exception {
        JustDireEarthquakeTestSupport.bind();
        stomp = method(JustDireGroundStompCompatibility.class, "activate", JustDireAbilityTarget.class, ItemStack.class, int.class, boolean.class);
        earthquake = method(JustDireEarthquakeCompatibility.class, "activate", JustDireAbilityTarget.class, ItemStack.class, int.class, boolean.class);
        passives = method(JustDireSettingsCompatibility.class, "toggleBoundPassives", JustDireAbilityTarget.class, int.class, boolean.class);
        stompTick = method(JustDireGroundStompCompatibility.class, "tickEquipped", ServerPlayer.class);
        var records = new ArrayList<String>();
        actions(server); records.add("production location actions: both orientations in hotbar/main/FEET/offhand, native GS/EQ effects and 55000 FE; per-ability location/device filters and unchanged sources");
        invalidation(server); records.add("production locations: moved/equal-replaced/unsupported/native/invalid-count targets refused; GS extraction move/replacement rolls back FE; EQ replacement stops debit/effect after admitted lockout; wire inventory entry remains disabled");
        passiveAndClocks(server); records.add("production location passives: Step/Jump/Lava/Negate toggle free disabled/depleted; actual GS/EQ inventory pause and FEET-only resume");
        breaks(server); records.add("production location break: inventory/hotbar/offhand/FEET stats and native slot callbacks, once-only credits, preserved unrelated armor and actual Soul Speed effect; refund-time replacement never receives break cleanup");
        return records;
    }

    private static void actions(MinecraftServer server) throws Exception {
        for (boolean inverse : new boolean[]{false, true}) {
            for (int slot : new int[]{0, 9, JustDireAbilityTarget.FEET_SLOT, 40}) {
                var player = player(server); var root = root(100_000, inverse); configure(root, false, false, List.of("groundstomp", "earthquake"));
                player.getInventory().setItem(slot, root); var target = new JustDireAbilityTarget(player, slot, root);
                var history = ModContent.getMixedBootsData(root); var mob = mob(player);
                try {
                    var expected = JustDireActiveState.snapshot(root);
                    require(use(stomp, target, expected) && use(earthquake, target, expected), "Internal location action rejected");
                    require(charge(root) == 45_000 && mob.hasEffect(MobEffects.SLOWNESS) && mob.getDeltaMovement().horizontalDistance() > 0
                            && history.equals(ModContent.getMixedBootsData(root)), "Location effects/resources/sources differ");
                } finally { mob.discard(); }
                configure(root, true, false, List.of("groundstomp"));
                require(JustDireSettingsCompatibility.groundStompBinding(target, KEY, false) == (slot == JustDireAbilityTarget.FEET_SLOT), "Held/inventory treated as equipped");
                configure(root, false, true, List.of("groundstomp"));
                require(JustDireSettingsCompatibility.groundStompBinding(target, 4, true)
                        && !JustDireSettingsCompatibility.groundStompBinding(target, 4, false), "Mouse binding location/device mismatch");
            }
        }
    }

    private static void invalidation(MinecraftServer server) throws Exception {
        var player = player(server); var root = root(100_000, false); configure(root, false, false, List.of("groundstomp", "earthquake"));
        player.getInventory().setItem(9, root); var target = new JustDireAbilityTarget(player, 9, root); var expected = JustDireActiveState.snapshot(root);
        player.getInventory().setItem(9, ItemStack.EMPTY); player.getInventory().setItem(10, root);
        require(!use(stomp, target, expected) && !use(earthquake, target, expected) && charge(root) == 100_000, "Moved root followed");
        player.getInventory().setItem(9, root.copy());
        require(!use(stomp, target, expected), "Equal replacement accepted");
        for (int slot : new int[]{-1, 37, 38, 39, 41, 100}) {
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) { player.getInventory().setItem(slot, root); }
            var unsupported = new JustDireAbilityTarget(player, slot, root);
            require(!unsupported.valid() && !use(stomp, unsupported, expected), "Unsupported slot accepted");
            if (slot >= 0 && slot < player.getInventory().getContainerSize()) { player.getInventory().setItem(slot, ItemStack.EMPTY); }
        }
        player.getInventory().setItem(9, JustDireEarthquakeTestSupport.original(100_000));
        require(!new JustDireAbilityTarget(player, 9, player.getInventory().getItem(9)).valid(), "Native item accepted as mixed");
        player.getInventory().setItem(9, root); root.setCount(2);
        require(!target.valid() && !use(stomp, target, JustDireActiveState.snapshot(root)), "Invalid count accepted"); root.setCount(1);
        for (Method action : List.of(stomp, earthquake)) {
            for (boolean move : new boolean[]{false, true}) {
                var paid = root(100_000, false); configure(paid, false, false, List.of("groundstomp", "earthquake"));
                player.getInventory().setItem(9, paid); var current = new JustDireAbilityTarget(player, 9, paid); var victim = mob(player);
                JustDireGroundStompServerGuardPlugin.debitMutation = () -> {
                    player.getInventory().setItem(9, move ? ItemStack.EMPTY : paid.copy());
                    if (move) { player.getInventory().setItem(10, paid); }
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
                } finally { JustDireGroundStompServerGuardPlugin.debitMutation = null; victim.discard(); }
            }
        }
        player.getInventory().setItem(9, root); player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, KEY, false)).accepted(), "Inventory wire entry unlocked in A");
    }

    private static void passiveAndClocks(MinecraftServer server) throws Exception {
        var player = player(server); var root = root(0, false);
        var names = List.of("stepheight", "jumpboost", "lavarepair", "negatefalldamage"); configure(root, false, false, names);
        set(root, "tool_enabled", false); player.getInventory().setItem(9, root);
        var target = new JustDireAbilityTarget(player, 9, root); var before = root.copy();
        require(JustDireSettingsCompatibility.boundPassives(target, KEY, false).equals(names) && (boolean) passives.invoke(null, target, KEY, false), "Free inventory passive dispatch rejected");
        for (String name : names) { set(before, name + "_toggle", false); }
        require(ItemStack.matches(root, before), "Inventory passives changed values/resources/sources");
        set(root, "ability_cooldowns", List.of(JustDireEarthquakeTestSupport.timer("groundstomp", 2, false), JustDireEarthquakeTestSupport.timer("earthquake", 1, true)));
        before = root.copy(); stompTick.invoke(null, player); JustDireEarthquakeTestSupport.tick(player);
        require(ItemStack.matches(root, before), "Off-slot timers advanced");
        player.getInventory().setItem(9, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.FEET, root);
        stompTick.invoke(null, player); JustDireEarthquakeTestSupport.tick(player);
        require(JustDireGroundStompTestSupport.timer(root) == 1
                && JustDireEarthquakeTestSupport.clock(root, "earthquake").equals(new JustDireEarthquakeTestSupport.Clock(800, false)), "Worn-only clocks failed to resume");
    }

    private static void breaks(MinecraftServer server) throws Exception {
        JustDireRefundBreakTest.register();
        try {
            for (int slot : new int[]{9, 0, 40, JustDireAbilityTarget.FEET_SLOT}) {
                var player = new NeoForgeJustDireGroundStompServerTest.BreakObserver(server);
                var root = JustDireRefundBreakTest.mixed("blazegold", "groundstomp"); configure(root, false, false, List.of("groundstomp"));
                root.setDamageValue(root.getMaxDamage() - 25); var item = root.getItem();
                JustDireRefundBreakTest.observe(player, root);
                var worn = new ItemStack(Items.NETHERITE_BOOTS);
                worn.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SOUL_SPEED), 3);
                player.setItemSlot(EquipmentSlot.FEET, worn); player.getInventory().setItem(slot, root);
                if (slot != JustDireAbilityTarget.FEET_SLOT) {
                    player.level().setBlock(player.blockPosition().below(), Blocks.SOUL_SAND.defaultBlockState(), 3); player.setOnGround(true);
                    worn.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
                    EnchantmentHelper.runLocationChangedEffects(player.level(), worn, player, EquipmentSlot.FEET);
                    require(!player.activeLocationDependentEnchantments(EquipmentSlot.FEET).isEmpty(), "Soul Speed witness did not activate");
                }
                double armor = player.getAttributeValue(Attributes.ARMOR), speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                var equipment = slot == 9 ? null : slot == 0 ? EquipmentSlot.MAINHAND : slot == 40 ? EquipmentSlot.OFFHAND : EquipmentSlot.FEET;
                require(use(stomp, new JustDireAbilityTarget(player, slot, root), JustDireActiveState.snapshot(root)) && root.isEmpty()
                        && player.callbacks == (equipment == null ? 0 : 1) && player.brokenSlot == equipment
                        && player.getStats().getValue(Stats.ITEM_BROKEN.get(item)) == 1, "Break location/feedback/stat mismatch");
                JustDireRefundBreakTest.refunds("groundstomp");
                if (slot != JustDireAbilityTarget.FEET_SLOT) {
                    require(player.getItemBySlot(EquipmentSlot.FEET) == worn && player.getAttributeValue(Attributes.ARMOR) == armor
                            && player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed
                            && !player.activeLocationDependentEnchantments(EquipmentSlot.FEET).isEmpty(), "Inventory/hand break cleaned up unrelated footwear");
                }
            }
            // Refund emission occurs after shrink but before onBreak: replace the slot at that exact callback boundary.
            var player = new NeoForgeJustDireGroundStompServerTest.BreakObserver(server);
            var root = JustDireRefundBreakTest.mixed("blazegold", "groundstomp"); configure(root, false, false, List.of("groundstomp"));
            var item = root.getItem();
            root.setDamageValue(root.getMaxDamage() - 25); player.setItemSlot(EquipmentSlot.FEET, root);
            JustDireRefundBreakTest.observe(player, root); var replacement = new ItemStack(Items.NETHERITE_BOOTS);
            JustDireRefundBreakTest.onRefund(() -> {
                player.setItemSlot(EquipmentSlot.FEET, replacement);
                replacement.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
            });
            require(use(stomp, new JustDireAbilityTarget(player, JustDireAbilityTarget.FEET_SLOT, root), JustDireActiveState.snapshot(root))
                    && root.isEmpty() && player.callbacks == 0 && player.getStats().getValue(Stats.ITEM_BROKEN.get(item)) == 1
                    && player.getAttributeValue(Attributes.ARMOR) == 3
                    && player.getItemBySlot(EquipmentSlot.FEET) == replacement, "Refund-time replacement received break cleanup");
            JustDireRefundBreakTest.refunds("groundstomp");
        } finally { JustDireRefundBreakTest.clearObservation(); }
    }

    private static ItemStack root(int amount, boolean inverse) {
        var pair = MixedBootsFactory.createPair(JustDireEarthquakeTestSupport.original(0), JustDireGroundStompTestSupport.boots("blazegold", 0));
        var root = inverse ? pair.inverse() : pair.primary(); set(root, "forge_energy", amount); return root;
    }
    private static void configure(ItemStack root, boolean equipped, boolean mouse, List<String> names) throws Exception {
        var constructor = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class); var records = new ArrayList<Object>();
        for (String name : names) {
            if (!name.equals("lavarepair")) { set(root, name + "_upgrade_installed", true); }
            set(root, name + "_toggle", true); set(root, name + "_bindingmode", 2);
            records.add(constructor.newInstance(name, mouse ? 4 : KEY, mouse, equipped));
        }
        set(root, "ability_bindings", List.copyOf(records));
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception { var result = type.getDeclaredMethod(name, args); result.setAccessible(true); return result; }
    private static boolean use(Method action, JustDireAbilityTarget target, ItemStack expected) throws Exception { return (boolean) action.invoke(null, target, expected, KEY, false); }
    private static ServerPlayer player(MinecraftServer server) { return JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL); }
    private static Mob mob(ServerPlayer player) {
        var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND); require(mob != null, "No action target");
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(player.getX() + 1, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob), "Target not inserted"); mob.setOnGround(true); return mob;
    }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static <T> void set(ItemStack root, String name, T value) { JustDireAttributeTestSupport.set(root, name, value); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
