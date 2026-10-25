package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility.Provider;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Production creation/query/preservation gate; no fixture action, tick or preservation mixin. */
public final class NeoForgeJustDireGroundStompStateTest {
    private static final List<String> LIVE = List.of("ability_bindings", "groundstomp_bindingmode",
                                                     "groundstomp_custom_setting", "ability_cooldowns");
    private static final String PREFIX = "just-dire-ground-stomp-state";
    private static boolean finished;
    private static int groups;
    private static final StringBuilder report = new StringBuilder();

    private NeoForgeJustDireGroundStompStateTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
            String variant = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompStateVariant", "live");
            if (variant.equals("absent")) {
                require(!JustDireGroundStompCompatibility.available(), "Absent API became available");
                var root = workbench(player, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
                require(JustDireGroundStompCompatibility.quote(root) == null, "Absent mod granted quote");
                require(!MixedBootsRecomposer.recompose(root, new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).isEmpty(),
                        "Absent mod broke ordinary recomposition");
                record("absent JDT: actual Workbench creation/recomposition and quiet rejected quote");
            } else if (!variant.equals("live")) {
                failure(player, variant);
            } else {
                require(JustDireGroundStompCompatibility.available(), "Ground Stomp binding unavailable");
                creation(player);
                quotes(player);
                preservation(player);
            }
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " production state/query groups\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(PREFIX, failure);
        } finally {
            server.halt(false);
        }
    }

    private static void creation(ServerPlayer player) throws Exception {
        for (int[] timers : List.of(new int[]{40, 180}, new int[]{90, 90}, new int[]{0, 70}, new int[]{0, 0})) {
            var a = boots("blazegold", 0);
            var b = boots("celestigem", 10_000);
            if (timers[0] > 0) {
                a.set(timers(), new ArrayList<>(List.of(timer("groundstomp", timers[0], false), timer("other_fixture", 77, true))));
            }
            if (timers[1] > 0) {
                b.set(timers(), new ArrayList<>(List.of(timer("groundstomp", timers[1], false), timer("airburst", 31, false))));
            }
            var beforeA = a.copy();
            var beforeB = b.copy();
            var outputs = workbench(player, a, b);
            for (int orientation = 0; orientation < 2; orientation++) {
                var root = outputs.get(orientation);
                int expected = Math.max(timers[0], timers[1]);
                require(remaining(root) == expected, "Creation averaged/lost conflicting cooldown " + List.of(timers[0], timers[1]));
                var owner = orientation == 0 ? beforeA : beforeB;
                require(unrelated(root).equals(unrelated(owner)), "Creation unioned unrelated timers instead of owner projection");
                require(charge(root) == 5_000 && root.getOrDefault(flag(), false), "Timer import changed storage/install union");
                require(expected == 0 || JustDireGroundStompCompatibility.quote(root) == null, "Imported timer granted readiness");
            }
            if (timers[0] > 0 && timers[1] > 0) {
                require(outputs.getFirst().get(timers()) != a.get(timers()) && outputs.getLast().get(timers()) != b.get(timers())
                                && outputs.getFirst().get(timers()) != outputs.getLast().get(timers()),
                        "Output timer lists alias native inputs/inverse");
            }
            var first = outputs.getFirst();
            var inverseBefore = outputs.getLast().copy();
            first.set(timers(), List.of(timer("groundstomp", 1, false)));
            require(ItemStack.matches(inverseBefore, outputs.getLast()) && ItemStack.matches(beforeA, a) && ItemStack.matches(beforeB, b),
                    "Creation/root edit changed inverse or inputs");
            record("Workbench ordinary/shift-click inverse takes: timers " + timers[0] + "/" + timers[1]
                           + " -> max; projected unrelated records; independent state/unchanged inputs; no bill");
        }

        var forged = new ItemStack(Items.DIAMOND_BOOTS);
        forged.set(timers(), List.of(timer("groundstomp", 200, false)));
        forged.set(flag(), true);
        for (var root : workbench(player, forged, boots("blazegold", 0))) {
            require(remaining(root) == 0 && JustDireGroundStompCompatibility.quote(root) != null, "Ineligible timer prevented legitimate readiness");
        }
        var ferricore = boots("ferricore", 0);
        ferricore.set(timers(), List.of(timer("groundstomp", 199, false)));
        for (var root : workbench(player, ferricore, new ItemStack(Items.DIAMOND_BOOTS))) {
            require(remaining(root) == 0 && !root.getOrDefault(flag(), false) && JustDireGroundStompCompatibility.quote(root) == null,
                    "Ineligible tier forged Ground Stomp authorization");
        }
        record("forged vanilla/Ferricore Ground Stomp timers excluded independently of owner; ineligible installation cannot authorize");

        var config = config();
        boolean enabled = config.get();
        try {
            config.set(false);
            var source = boots("celestigem", 0);
            source.set(flag(), false); // Cooldown ownership is independent of source installation and current usability.
            source.set(timers(), List.of(timer("groundstomp", 123, false)));
            for (var root : workbench(player, source, boots("blazegold", 0))) {
                require(remaining(root) == 123 && root.getOrDefault(flag(), false) && JustDireGroundStompCompatibility.quote(root) == null,
                        "Disabled config/empty FE erased permanently eligible pending cooldown");
            }
        } finally {
            config.set(enabled);
        }
        record("permanent-tier timer import survives disabled config, zero FE and missing historical installation");

        for (Object unsupported : List.of(timer("groundstomp", 15, true), timer("groundstomp", -1, false))) {
            var source = boots("blazegold", 0);
            source.set(timers(), List.of(unsupported));
            for (var root : workbench(player, new ItemStack(Items.DIAMOND_BOOTS), source)) {
                require(root.get(timers()).contains(unsupported) && JustDireGroundStompCompatibility.quote(root) == null,
                        "Unsupported non-owner timer minted readiness");
                require(same(root, rebuild(root)), "Unsupported stored timer lost on recomposition");
            }
        }
        record("typed active/negative Ground Stomp records retained from either side; quotes deny without clearing or conversion; warning bounded");
    }

    private static void quotes(ServerPlayer player) throws Exception {
        for (var tiers : List.of(List.of("eclipsealloy", "blazegold"), List.of("celestigem", "blazegold"),
                                 List.of("celestigem", "eclipsealloy"))) {
            for (var root : workbench(player, boots(tiers.getFirst(), 10_000), boots(tiers.getLast(), 10_000))) {
                root.remove(value());
                Provider powered = tiers.contains("eclipsealloy") ? Provider.ECLIPSE : Provider.CELESTIGEM;
                checkQuote(root, powered, powered == Provider.ECLIPSE ? 5 : 3);
                root.set(value(), 2);
                checkQuote(root, powered, 2); // Equal effective values prefer tier, not cheap wear or source order.
                root.set(value(), 99);
                checkQuote(root, powered, powered == Provider.ECLIPSE ? 5 : 3);
                require(root.get(value()) == 99, "Query overwrote configured slider");
                root.set(JustDireResourceTestSupport.energyComponent(), 4_999);
                if (tiers.contains("blazegold")) {
                    checkQuote(root, Provider.BLAZEGOLD, 2);
                } else {
                    rejected(root, "depleted powered-only pair");
                }
                root.set(JustDireResourceTestSupport.energyComponent(), 5_000);
                checkQuote(root, powered, powered == Provider.ECLIPSE ? 5 : 3);
                for (String control : List.of("tool_enabled", "groundstomp_toggle", "groundstomp_upgrade_installed")) {
                    var component = JustDireAttributeTestSupport.<Boolean>component(control);
                    root.set(component, false);
                    rejected(root, control + " false");
                    root.remove(component);
                    if (control.equals("groundstomp_upgrade_installed")) {
                        rejected(root, control + " absent");
                        root.set(component, true);
                    }
                }
                root.set(timers(), List.of(timer("earthquake", 77, true)));
                checkQuote(root, powered, powered == Provider.ECLIPSE ? 5 : 3);
                for (int pending : new int[]{0, 1, 200}) {
                    root.set(timers(), List.of(timer("groundstomp", pending, false)));
                    rejected(root, "pending native timer " + pending);
                }
            }
            record("both orientations " + tiers + ": defaults, effective-value ties, clamps without edits, 4999/5000 FE, fallback, root controls, unrelated and pending records; all queries read-only");
        }
        for (var root : workbench(player, boots("blazegold", 0), new ItemStack(Items.DIAMOND_BOOTS))) {
            for (int remaining : new int[]{24, 25, 26}) {
                root.setDamageValue(root.getMaxDamage() - remaining);
                if (remaining < 25) {
                    rejected(root, "insufficient root durability");
                } else {
                    checkQuote(root, Provider.BLAZEGOLD, 2);
                }
            }
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            for (int remaining : new int[]{24, 25}) {
                root.setDamageValue(root.getMaxDamage() - remaining);
                var original = boots("blazegold", 0);
                original.setDamageValue(original.getMaxDamage() - remaining);
                original.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
                var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
                require((boolean) tool.getMethod("canUseAbilityAndDurability", ItemStack.class, ability)
                                      .invoke(original.getItem(), original, ability.getField("GROUNDSTOMP").get(null)) == (remaining >= 25),
                        "Native Unbreakable base eligibility changed");
                if (remaining < 25) {
                    rejected(root, "Unbreakable still needs base budget");
                } else {
                    checkQuote(root, Provider.BLAZEGOLD, 2);
                }
            }
            var nativeSource = ModContent.getMixedBootsData(root).orElseThrow();
            var dormant = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            rejected(dormant, "dormant tier");
            var restored = MixedBootsRecomposer.recompose(dormant, nativeSource.left().source(), nativeSource.right().source());
            restored.setDamageValue(0);
            checkQuote(restored, Provider.BLAZEGOLD, 2);
        }
        var config = config();
        boolean enabled = config.get();
        try {
            config.set(false);
            for (var root : workbench(player, boots("eclipsealloy", 10_000), boots("blazegold", 0))) {
                rejected(root, "native config disabled");
            }
        } finally {
            config.set(enabled);
        }
        require(JustDireGroundStompCompatibility.quote(boots("eclipsealloy", 10_000)) == null
                        && JustDireGroundStompCompatibility.quote(new ItemStack(Items.DIAMOND_BOOTS)) == null,
                "Non-mixed native/ordinary root granted mixed quote");
        record("actual root durability 24/25/26, native-equivalent Unbreakable base eligibility, dormant/returning tiers, native config disabling and non-mixed rejection; no payment/break/effects");

        var history = boots("eclipsealloy", 10_000);
        history.set(flag(), false);
        history.set(value(), 1);
        JustDireAttributeTestSupport.set(history, "tool_enabled", false);
        JustDireAttributeTestSupport.set(history, "groundstomp_toggle", false);
        for (var root : workbench(player, history, new ItemStack(Items.DIAMOND_BOOTS))) {
            root.set(flag(), true);
            root.remove(value());
            JustDireAttributeTestSupport.set(root, "tool_enabled", true);
            JustDireAttributeTestSupport.set(root, "groundstomp_toggle", true);
            checkQuote(root, Provider.ECLIPSE, 5);
            root.setCount(2);
            rejected(root, "count-two root");
        }
        record("root-only installation/live enablement overrides disabled/uninstalled source history; absent root slider uses tier default, not historical 1; count-two root rejects");
    }

    private static void preservation(ServerPlayer player) throws Exception {
        var source = boots("celestigem", 10_000);
        source.set(timers(), List.of(timer("groundstomp", 45, false), timer("airburst", 99, true)));
        source.set(JustDireAttributeTestSupport.component("ability_bindings"), List.of(binding("groundstomp", 71), binding("earthquake", 72)));
        JustDireAttributeTestSupport.set(source, "groundstomp_bindingmode", 2);
        JustDireAttributeTestSupport.set(source, "groundstomp_custom_setting", 0);
        for (var root : workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
            for (boolean absent : new boolean[]{false, true}) {
                var current = root.copy();
                if (absent) {
                    LIVE.forEach(path -> current.remove(JustDireAttributeTestSupport.component(path)));
                }
                current.set(JustDireResourceTestSupport.energyComponent(), 123);
                current.setDamageValue(31);
                var data = ModContent.getMixedBootsData(current).orElseThrow();
                var left = data.left().source();
                var right = data.right().source();
                for (var changed : List.of(left, right)) {
                    JustDireAttributeTestSupport.set(changed, "groundstomp_bindingmode", 0);
                    JustDireAttributeTestSupport.set(changed, "groundstomp_custom_setting", 1);
                    changed.set(JustDireAttributeTestSupport.component("ability_bindings"), List.of(binding("groundstomp", 80)));
                    changed.set(timers(), List.of(timer("groundstomp", 199, false)));
                }
                require(same(current, MixedBootsRecomposer.recompose(current, left, right)),
                        "Changed historical projection replaced live equality/absence");
                var before = current.copy();
                var smith = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), BlockPos.ZERO));
                smith.getSlot(SmithingMenu.TEMPLATE_SLOT).set(item("template_eclipsealloy"));
                smith.getSlot(SmithingMenu.BASE_SLOT).set(current.copy());
                smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(item("eclipsealloy_ingot"));
                var preview = smith.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
                require(!preview.isEmpty() && same(current, preview) && charge(preview) == 123 && preview.getDamageValue() == 31,
                        "Real tier preview reset state/FE/wear");
                var transformed = ModContent.getMixedBootsData(preview).orElseThrow();
                require(transformed.left().source().is(item("eclipsealloy_boots").getItem())
                                || transformed.right().source().is(item("eclipsealloy_boots").getItem()), "Tier did not transform native leg");
                player.getInventory().clearContent();
                smith.clicked(SmithingMenu.RESULT_SLOT, 0, absent ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, player);
                var taken = absent ? player.getInventory().getNonEquipmentItems().stream()
                                           .filter(stack -> ItemStack.matches(preview, stack)).findFirst().orElseThrow() : smith.getCarried();
                require(ItemStack.matches(preview, taken) && ItemStack.matches(before, current)
                                && smith.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                                && smith.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                                && smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                        "Tier take mutated old root or consumed wrong inputs");
            }
        }
        record("both orientations: production recomposition equality/explicit absence with deliberately changed projections; real tier preview/ordinary and shift-click take; unrelated records, 123 FE and wear31 retained without fixture preservation");
    }

    private static void failure(ServerPlayer player, String variant) throws Exception {
        require(JustDireGroundStompCompatibility.available() == variant.equals("queryFailure"), "Wrong binding failure variant");
        for (var root : workbench(player, boots("eclipsealloy", 10_000), boots("blazegold", 0))) {
            root.set(timers(), List.of(timer("earthquake", 99, true)));
            root.set(JustDireAttributeTestSupport.component("ability_bindings"), List.of(binding("groundstomp", 71)));
            JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
            JustDireAttributeTestSupport.set(root, "groundstomp_custom_setting", 1);
            for (int repeat = 0; repeat < 3; repeat++) {
                rejected(root, variant);
            }
            require(!JustDireGroundStompCompatibility.available() && JustDireUpgradesCompatibility.available()
                            && JustDireResourceTestSupport.handler(root).getAmountAsInt() == 5_000,
                    "Active binding failure poisoned root upgrades/storage");
            require(same(root, rebuild(root)), "Failed optional binding disabled registry-only preservation");
        }
        record(variant + ": both orientations; repeated read-only rejection with one diagnostic; installation/storage and registry-only live preservation remain independent");
    }

    static List<ItemStack> workbench(ServerPlayer player, ItemStack a, ItemStack b) {
        player.getInventory().clearContent();
        var beforeA = a.copy();
        var beforeB = b.copy();
        var menu = new MismatchedWorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), BlockPos.ZERO));
        menu.getSlot(0).set(a.copy());
        menu.getSlot(1).set(b.copy());
        var first = menu.getSlot(2).getItem().copy();
        var second = menu.getSlot(3).getItem().copy();
        if (first.isEmpty() || second.isEmpty()) {
            // Surface assembly errors instead of treating an empty preview as an opaque menu failure.
            MixedBootsFactory.createPair(a, b);
            throw new AssertionError("Workbench recipe/preview missing for " + a + " / " + b);
        }
        require(!first.isEmpty() && !second.isEmpty() && ItemStack.matches(beforeA, a) && ItemStack.matches(beforeB, b),
                "Workbench previews invalid/changed inputs");
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        require(ItemStack.matches(first, menu.getCarried()) && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                "First Workbench take did not consume once");
        menu.clicked(3, 0, ContainerInput.QUICK_MOVE, player);
        require(player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(second, stack))
                        && menu.getSlot(3).getItem().isEmpty(), "Inverse Workbench shift-click failed");
        return List.of(first, second);
    }

    private static void checkQuote(ItemStack root, Provider provider, int strength) {
        var before = root.copy();
        var quote = JustDireGroundStompCompatibility.quote(root);
        require(quote != null && quote.provider() == provider && quote.strength() == strength
                        && quote.baseCost() == (provider.powered() ? 5_000 : 25) && quote.cooldownTicks() == 200,
                "Unexpected production quote: " + quote + " expected " + provider + "/" + strength);
        require(ItemStack.matches(before, root), "Quote mutated live resources/controls/provenance");
    }

    private static void rejected(ItemStack root, String label) {
        var before = root.copy();
        require(JustDireGroundStompCompatibility.quote(root) == null && ItemStack.matches(before, root), "Rejected quote mutated/granted " + label);
    }

    private static ItemStack rebuild(ItemStack root) {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        return MixedBootsRecomposer.recompose(root, data.right().source(), data.left().source());
    }

    private static boolean same(ItemStack before, ItemStack after) {
        return LIVE.stream().allMatch(path -> Objects.equals(before.get(JustDireAttributeTestSupport.component(path)),
                                                             after.get(JustDireAttributeTestSupport.component(path))));
    }

    private static int remaining(ItemStack root) throws Exception {
        int maximum = 0;
        for (Object record : root.getOrDefault(timers(), List.of())) {
            if (record.getClass().getMethod("abilityName").invoke(record).equals("groundstomp")) {
                maximum = Math.max(maximum, (int) record.getClass().getMethod("cooldownTicks").invoke(record));
            }
        }
        return maximum;
    }

    private static List<Object> unrelated(ItemStack stack) throws Exception {
        var records = new ArrayList<Object>();
        for (Object record : stack.getOrDefault(timers(), List.of())) {
            if (!record.getClass().getMethod("abilityName").invoke(record).equals("groundstomp")) {
                records.add(record);
            }
        }
        return records;
    }

    private static Object timer(String name, int ticks, boolean active) throws Exception {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                    .getConstructor(String.class, int.class, boolean.class).newInstance(name, ticks, active);
    }

    private static Object binding(String name, int key) throws Exception {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                    .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance(name, key, false, true);
    }

    private static ModConfigSpec.BooleanValue config() throws Exception {
        var map = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        return (ModConfigSpec.BooleanValue) map.entrySet().stream().filter(entry -> ((Enum<?>) entry.getKey()).name().equals("GROUNDSTOMP"))
                                               .findFirst().orElseThrow().getValue();
    }

    private static ItemStack boots(String tier, int charge) {
        var result = NeoForgeJustDireResourcesTest.boots(tier, charge);
        result.set(flag(), true);
        return result;
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", path)));
    }

    private static DataComponentType<Boolean> flag() {
        return JustDireAttributeTestSupport.component("groundstomp_upgrade_installed");
    }

    private static DataComponentType<Integer> value() {
        return JustDireAttributeTestSupport.component("groundstomp_value");
    }

    private static DataComponentType<List<Object>> timers() {
        return JustDireAttributeTestSupport.component("ability_cooldowns");
    }

    private static int charge(ItemStack root) {
        return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
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
