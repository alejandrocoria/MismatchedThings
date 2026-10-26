package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Production-only state/quote gate; does not forward the research candidate's action or tick. */
final class NeoForgeJustDireEarthquakeStateTest {
    private static final String PREFIX = "just-dire-earthquake-state";
    private static final List<String> LIVE = List.of("earthquake_upgrade_installed",
                                                     "earthquake_toggle",
                                                     "earthquake_value",
                                                     "earthquake_bindingmode",
                                                     "earthquake_custom_setting",
                                                     "ability_bindings",
                                                     "ability_cooldowns",
                                                     "tool_enabled");
    private static boolean finished;
    private static int groups;
    private static final StringBuilder report = new StringBuilder();

    private NeoForgeJustDireEarthquakeStateTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
            String variant = System.getProperty("mismatchedboots.runtimetest.justDireEarthquakeStateVariant", "live");
            if (variant.equals("absent")) {
                require(!JustDireEarthquakeCompatibility.available(), "Absent Earthquake API became available");
                var root = workbench(player, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
                rejected(root, "absent mod");
                require(!rebuild(root).isEmpty(), "Absent mod broke ordinary recomposition");
                record("absent JDT: ordinary Workbench and recomposition; no quote or optional loading warning");
            } else if (!variant.equals("live")) {
                failure(player, variant);
            } else {
                require(JustDireEarthquakeCompatibility.available(), "Earthquake API unavailable");
                creation(player);
                quotes(player);
                preservation(player);
                var root = workbench(player, boots("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
                charge(root, 50_000);
                root.set(JustDireAttributeTestSupport.component("earthquake_bindingmode"), 2);
                root.set(JustDireAttributeTestSupport.component("ability_bindings"), List.of(binding()));
                player.setItemSlot(EquipmentSlot.FEET, root);
                var before = root.copy();
                require(JustDireSettingsCompatibility.inputBinding(root, 71, false)
                                && JustDireSettingsCompatibility.metadata(root, "earthquake") != null
                                && JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false)).accepted()
                                && ItemStack.matches(before, root), "Supported metadata/input or read-only preparation changed state");
                record("state gate invokes no action/tick; C advertises supported native metadata/input and B server preparation remains read-only");
            }
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " production Earthquake state groups\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(PREFIX, failure);
        } finally {
            server.halt(false);
        }
    }

    private static void creation(ServerPlayer player) throws Exception {
        for (int[] pair : List.of(new int[]{200, 1, 800, 0}, new int[]{80, 1, 900, 0},
                                  new int[]{100, 1, 900, 0}, new int[]{Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0},
                                  new int[]{0, 0, 70, 0}, new int[]{0, 0, 0, 0})) {
            var a = boots("eclipsealloy");
            var b = boots("eclipsealloy");
            a.set(timers(), new ArrayList<>(List.of(timer("groundstomp", 30, false), timer("airburst", 31, false))));
            b.set(timers(), new ArrayList<>(List.of(timer("groundstomp", 90, false))));
            if (pair[0] > 0) {
                a.get(timers()).add(timer("earthquake", pair[0], pair[1] == 1));
            }
            if (pair[2] > 0) {
                b.get(timers()).add(timer("earthquake", pair[2], pair[3] == 1));
            }

            var beforeA = a.copy();
            var beforeB = b.copy();
            long left = pair[0] == 0 ? 0 : (long) pair[0] + (pair[1] == 1 ? 800 : 0);
            long right = pair[2] == 0 ? 0 : (long) pair[2] + (pair[3] == 1 ? 800 : 0);
            boolean chooseLeft = left > right || left == right && pair[1] == 1;
            Object expected = Math.max(left, right) == 0 ? null
                                                         : timer("earthquake", pair[chooseLeft ? 0 : 2], pair[chooseLeft ? 1 : 3] == 1);
            var roots = workbench(player, a, b);
            for (int orientation = 0; orientation < 2; orientation++) {
                var root = roots.get(orientation);
                require(earthquakes(root).equals(expected == null ? List.of() : List.of(expected)), "Wrong total-lockout/phase import");
                require(root.get(timers()).contains(timer("groundstomp", 90, false)), "Earthquake erased Ground Stomp maximum");
                require(root.get(timers()).contains(timer("airburst", 31, false)) == (orientation == 0), "Unrelated owner timer projection changed");
            }
            require(roots.getFirst().get(timers()) != roots.getLast().get(timers()), "Inverse clock lists alias");
            var inverse = roots.getLast().copy();
            roots.getFirst().set(timers(), List.of());
            require(ItemStack.matches(inverse, roots.getLast()) && ItemStack.matches(beforeA, a) && ItemStack.matches(beforeB, b),
                    "Import edited input/inverse history");
            record("Workbench preview/ordinary and shift take: " + left + "/" + right + " total lockout, native phase retained, ties/overflow safe; independent inverse/history; GS and owner airburst retained");
        }
        for (String tier : List.of("celestigem", "ferricore")) {
            var forged = boots(tier);
            forged.set(timers(), List.of(timer("earthquake", 199, true)));
            for (var root : workbench(player, forged, new ItemStack(Items.DIAMOND_BOOTS))) {
                require(earthquakes(root).isEmpty() && !root.getOrDefault(flag(), false), "Unsupported input imported forged Earthquake state");
                root.set(flag(), true);
                rejected(root, "forged " + tier);
            }
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            require(!((java.util.Set<?>) tool.getMethod("getAllAbilities").invoke(forged.getItem()))
                    .contains(ability.getField("EARTHQUAKE").get(null)), "Unsupported tier unexpectedly has permanent Earthquake ability");
        }

        var forgedVanilla = new ItemStack(Items.DIAMOND_BOOTS);
        forgedVanilla.set(flag(), true);
        forgedVanilla.set(timers(), List.of(timer("earthquake", 199, true)));
        var eligible = boots("eclipsealloy");
        eligible.set(timers(), List.of(timer("earthquake", 70, false)));
        for (var root : workbench(player, forgedVanilla, eligible)) {
            require(earthquakes(root).equals(eligible.get(timers())), "Vanilla owner forged a longer supported timer");
        }

        var original = boots("eclipsealloy");
        original.set(flag(), false);
        original.set(timers(), List.of(timer("earthquake", 100, true)));
        var config = config();
        boolean enabled = config.get();
        try {
            config.set(false);
            for (var root : workbench(player, new ItemStack(Items.DIAMOND_BOOTS), original)) {
                require(earthquakes(root).equals(original.get(timers())), "Disabled/uninstalled/empty input lost permanent-tier clock");
                rejected(root, "disabled config/import");
            }
        } finally {
            config.set(enabled);
        }
        record("both orientations: only permanent Eclipse imports timers; forged tiers excluded; disabled config, zero FE and absent historical installation do not erase lockout");

        for (var records : List.of(List.of(timer("earthquake", 0, true)), List.of(timer("earthquake", -1, false)),
                                   List.of(timer("earthquake", 20, true), timer("earthquake", 21, false)))) {
            var source = boots("eclipsealloy");
            source.set(timers(), records);
            for (var root : workbench(player, new ItemStack(Items.DIAMOND_BOOTS), source)) {
                require(earthquakes(root).equals(records), "Unsupported/ambiguous input silently cleared/merged");
                charge(root, 50_000);
                rejected(root, "unsupported/duplicate timer");
                require(same(root, rebuild(root)), "Unsupported evidence lost in recomposition");
            }
        }

        var malformed = boots("eclipsealloy");
        malformed.set(timers(), List.of(timer("earthquake", -1, true)));
        for (var root : workbench(player, eligible, malformed)) {
            require(earthquakes(root).containsAll(malformed.get(timers())) && earthquakes(root).containsAll(eligible.get(timers())),
                    "Valid other input discarded malformed lockout evidence");
            charge(root, 50_000);
            rejected(root, "valid plus malformed input");
        }
        record("non-positive and ambiguous duplicate records retained from non-owner as well; repeated queries deny without repairs and one bounded warning");
    }

    private static void quotes(ServerPlayer player) throws Exception {
        for (boolean two : new boolean[]{false, true}) {
            var history = boots("eclipsealloy");
            history.set(flag(), false);
            JustDireAttributeTestSupport.set(history, "forge_energy", 100_000);
            JustDireAttributeTestSupport.set(history, "tool_enabled", false);
            JustDireAttributeTestSupport.set(history, "earthquake_toggle", false);
            for (var root : workbench(player, history, two ? boots("eclipsealloy") : new ItemStack(Items.DIAMOND_BOOTS))) {
                root.set(flag(), true);
                JustDireAttributeTestSupport.set(root, "tool_enabled", true);
                JustDireAttributeTestSupport.set(root, "earthquake_toggle", true);
                for (int available : new int[]{0, 49_999, 50_000, 50_001}) {
                    charge(root, available);
                    checked(root, available >= 50_000);
                }
                for (int unbreaking : new int[]{3, 10}) {
                    root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), unbreaking);
                    charge(root, 49_999);
                    checked(root, false);
                    charge(root, 50_000);
                    checked(root, true);
                }
                root.set(JustDireAttributeTestSupport.component("earthquake_value"), -999);
                checked(root, true);
                for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
                    player.setGameMode(mode);
                    charge(root, 49_999);
                    checked(root, false);
                    charge(root, 50_000);
                    checked(root, true);
                }
                player.setGameMode(GameType.SURVIVAL);
                for (String name : List.of("tool_enabled", "earthquake_toggle", "earthquake_upgrade_installed")) {
                    var component = JustDireAttributeTestSupport.<Boolean>component(name);
                    root.set(component, false);
                    rejected(root, name);
                    root.remove(component);
                    checked(root, !name.equals("earthquake_upgrade_installed"));
                    root.set(component, true);
                }
                root.set(timers(), List.of(timer("groundstomp", 200, false), timer("airburst", 10, true)));
                checked(root, true);
                for (boolean active : new boolean[]{false, true}) {
                    root.set(timers(), List.of(timer("earthquake", 1, active)));
                    rejected(root, "pending phase");
                }
                root.remove(timers());
                root.setCount(2);
                rejected(root, "count two");
            }
        }

        var config = config();
        boolean enabled = config.get();
        try {
            config.set(false);
            for (var root : workbench(player, boots("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS))) {
                charge(root, 50_000);
                rejected(root, "native config veto");
            }
        } finally {
            config.set(enabled);
        }
        rejected(boots("eclipsealloy"), "original is not mixed");
        rejected(new ItemStack(Items.DIAMOND_BOOTS), "ordinary root");
        var nativeTool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        for (int amount : new int[]{49_999, 50_000}) {
            var source = NeoForgeJustDireResourcesTest.boots("eclipsealloy", amount);
            source.set(flag(), true);
            require((boolean) nativeTool.getMethod("canUseAbilityAndDurability", ItemStack.class, ability)
                                        .invoke(source.getItem(), source, ability.getField("EARTHQUAKE").get(null)) == (amount >= 50_000),
                    "Native base eligibility changed");
        }
        record("read-only quotes: orientations, one/two Eclipse, native 49999/50000 threshold, discount/exempt eligibility, root switches overriding history, unused numeric value, unrelated/own phases, native config and count/non-mixed rejection");
    }

    private static void preservation(ServerPlayer player) throws Exception {
        var source = boots("celestigem");
        JustDireAttributeTestSupport.set(source, "earthquake_bindingmode", 2);
        JustDireAttributeTestSupport.set(source, "earthquake_custom_setting", 0);
        for (var root : workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
            for (boolean absent : new boolean[]{false, true}) {
                var current = root.copy();
                current.set(timers(), List.of(timer("earthquake", 99, true), timer("groundstomp", 50, false), timer("airburst", 31, false)));
                current.set(JustDireAttributeTestSupport.component("ability_bindings"), List.of(binding()));
                rootFields(current);
                if (absent) {
                    LIVE.forEach(name -> current.remove(JustDireAttributeTestSupport.component(name)));
                }
                charge(current, 123);
                current.setDamageValue(31);
                var data = ModContent.getMixedBootsData(current).orElseThrow();
                var left = data.left().source();
                var right = data.right().source();
                for (var changed : List.of(left, right)) {
                    JustDireAttributeTestSupport.set(changed, "earthquake_bindingmode", 0);
                    JustDireAttributeTestSupport.set(changed, "earthquake_custom_setting", 1);
                    changed.set(timers(), List.of(timer("earthquake", 200, true)));
                }

                var recomposed = MixedBootsRecomposer.recompose(current, left, right);
                require(same(current, recomposed) && charge(recomposed) == 123 && recomposed.getDamageValue() == 31,
                        "Changed projection reset live state/absence/resources");
                player.getInventory().clearContent();
                var taken = JustDireGroundStompLifecycleTestSupport.smithing(player, current, absent);
                require(same(current, taken), "Real tier preview/take reset Earthquake state");
            }
        }
        record("both orientations/equality and explicit absence: changed source projection cannot restore managed Earthquake fields; actual tier preview and ordinary/shift take retain spent FE, wear, bindings and both clocks");
    }

    private static void failure(ServerPlayer player, String variant) throws Exception {
        require(JustDireEarthquakeCompatibility.available() == variant.equals("queryFailure"), "Wrong optional failure variant");
        for (var root : workbench(player, boots("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS))) {
            charge(root, 50_000);
            for (int repeat = 0; repeat < 3; repeat++) {
                rejected(root, variant);
            }
            require(!JustDireEarthquakeCompatibility.available() && JustDireUpgradesCompatibility.available()
                            && JustDireGroundStompCompatibility.quote(root) != null && JustDireResourceTestSupport.handler(root)
                                                                                                                  .getAmountAsInt() == 50_000,
                    "Earthquake failure poisoned Ground Stomp/root storage/installation");
            rootFields(root);
            root.set(timers(), List.of(timer("earthquake", 99, true)));
            require(same(root, rebuild(root)), "Unavailable API disabled registry-only Earthquake preservation");
            for (String name : LIVE) {
                root.remove(JustDireAttributeTestSupport.component(name));
            }
            require(same(root, rebuild(root)), "Unavailable API restored explicitly absent fields");
        }
        record(variant + ": repeated read-only denial/one diagnostic, intact Ground Stomp/storage/installation and registry-only presence/absence preservation");
    }

    private static void rootFields(ItemStack root) {
        JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", false);
        JustDireAttributeTestSupport.set(root, "earthquake_toggle", false);
        JustDireAttributeTestSupport.set(root, "earthquake_value", -999);
        JustDireAttributeTestSupport.set(root, "earthquake_bindingmode", 2);
        JustDireAttributeTestSupport.set(root, "earthquake_custom_setting", 0);
    }

    private static void checked(ItemStack root, boolean expected) {
        var before = root.copy();
        var quote = JustDireEarthquakeCompatibility.quote(root);
        require(expected ? quote != null && quote.baseCost() == 50_000 && quote.activeTicks() == 200 && quote.cooldownTicks() == 800
                         : quote == null, "Wrong Earthquake quote: " + quote);
        require(ItemStack.matches(before, root), "Quote changed root resources/controls/provenance");
    }

    private static void rejected(ItemStack root, String label) {
        var before = root.copy();
        require(JustDireEarthquakeCompatibility.quote(root) == null && ItemStack.matches(before, root), "Rejected quote mutated/granted " + label);
    }

    private static List<ItemStack> workbench(ServerPlayer player, ItemStack a, ItemStack b) {
        return NeoForgeJustDireGroundStompStateTest.workbench(player, a, b);
    }

    private static ItemStack rebuild(ItemStack root) {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        return MixedBootsRecomposer.recompose(root, data.right().source(), data.left().source());
    }

    private static boolean same(ItemStack before, ItemStack after) {
        return LIVE.stream()
                   .allMatch(name -> Objects.equals(before.get(JustDireAttributeTestSupport.component(name)),
                                                    after.get(JustDireAttributeTestSupport.component(name))));
    }

    private static List<Object> earthquakes(ItemStack root) throws Exception {
        var result = new ArrayList<>();
        for (Object record : root.getOrDefault(timers(), List.of())) {
            if (record.getClass().getMethod("abilityName").invoke(record).equals("earthquake")) {
                result.add(record);
            }
        }
        return result;
    }

    private static void charge(ItemStack root, int amount) {
        var handler = JustDireResourceTestSupport.handler(root);
        if (handler == null) {
            require(amount == 0, "Missing root energy handler");
            return;
        }
        try (var transaction = Transaction.openRoot()) {
            int before = handler.getAmountAsInt();
            require(handler.extract(before, transaction) == before && handler.insert(amount, transaction) == amount, "Fixture root charge failed");
            transaction.commit();
        }
    }

    private static int charge(ItemStack root) {
        return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
    }

    private static Object timer(String name, int ticks, boolean active) throws Exception {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                    .getConstructor(String.class, int.class, boolean.class).newInstance(name, ticks, active);
    }

    private static Object binding() throws Exception {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                    .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("earthquake", 71, false, true);
    }

    private static ModConfigSpec.BooleanValue config() throws Exception {
        var values = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        return (ModConfigSpec.BooleanValue) values.entrySet().stream().filter(entry -> ((Enum<?>) entry.getKey()).name().equals("EARTHQUAKE"))
                                                  .findFirst().orElseThrow().getValue();
    }

    private static ItemStack boots(String tier) {
        var stack = JustDireGroundStompTestSupport.boots(tier, 0);
        stack.set(flag(), true);
        return stack;
    }

    private static DataComponentType<Boolean> flag() {
        return JustDireAttributeTestSupport.component("earthquake_upgrade_installed");
    }

    private static DataComponentType<List<Object>> timers() {
        return JustDireAttributeTestSupport.component("ability_cooldowns");
    }

    private static void record(String message) {
        groups++;
        report.append(message).append('\n');
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
