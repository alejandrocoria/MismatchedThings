package games.alejandrocoria.mismatchedboots.runtimetest.betternether.fabric;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

public final class BetterNetherAdapterSpikeGameTests {
    private static final String EMPTY_STRUCTURE = "mismatchedboots_betternether_spike_test:empty";
    private static final String PRESENT_PROPERTY = "mismatchedboots.test.betterNether.present";
    private static final String ENABLED_PROPERTY = "mismatchedboots.test.betterNether.enabled";
    private static final String PRIORITY_PROPERTY = "mismatchedboots.test.betterNether.priority";

    @GameTest(structure = EMPTY_STRUCTURE)
    public void piglinAdapterIsConfigFaithfulAndOrderIndependent(GameTestHelper helper) {
        boolean present = requiredBooleanProperty(PRESENT_PROPERTY);
        boolean enabled = requiredBooleanProperty(ENABLED_PROPERTY);
        String priority = System.getProperty(PRIORITY_PROPERTY, "");
        require("high".equals(priority) || "low".equals(priority),
                "Missing expected Better Nether mixin-priority property");

        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        if (!present) {
            require(!BetterNetherSpikeProbe.apiCompatible(),
                    "Better Nether API guard passed while the mod was absent");
            assertVanillaControls(player);
            require(BetterNetherSpikeProbe.highEvaluations() == 0
                            && BetterNetherSpikeProbe.lowEvaluations() == 0,
                    "An optional Better Nether mixin ran while the mod was absent");
            helper.succeed();
            return;
        }

        require(BetterNetherSpikeProbe.apiCompatible(),
                "Exact Better Nether 26.201.2 API guard did not pass");
        require(priority.equals(BetterNetherSpikeProbe.selectedPriority()),
                "Mixin plugin did not select the requested priority");
        require(BetterNetherPiglinArmorAdapter.isEnabledForTest() == enabled,
                "Adapter did not read Better Nether's configured piglin rule");
        require(BetterNetherSpikeProbe.boundaryFailures() == 0,
                "Adapter could not resolve Better Nether's public runtime boundary");

        Player ordinaryPlayer = helper.makeMockServerPlayer(GameType.SURVIVAL);
        for (String path : List.of(
                "cincinnasite_boots", "nether_ruby_boots", "flaming_ruby_boots")) {
            ItemStack ordinary = item("betternether", path).getDefaultInstance();
            ordinaryPlayer.setItemSlot(EquipmentSlot.FEET, ordinary);
            require(PiglinAi.isWearingSafeArmor(ordinaryPlayer) == enabled,
                    path + " did not retain Better Nether's configured behavior");

            MixedBootsPair pair = MixedBootsFactory.createPair(
                    ordinary, Items.IRON_BOOTS.getDefaultInstance());
            assertMixedResult(player, pair.primary(), enabled, priority, path + " primary");
            assertMixedResult(player, pair.inverse(), enabled, priority, path + " inverse");
        }

        MixedBootsPair twoQualifying = MixedBootsFactory.createPair(
                item("betternether", "cincinnasite_boots").getDefaultInstance(),
                item("betternether", "flaming_ruby_boots").getDefaultInstance());
        assertMixedResult(player, twoQualifying.primary(), enabled, priority,
                "two qualifying sources");

        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.LEATHER_BOOTS.getDefaultInstance());
        assertMixedResult(player, unrelated.primary(), false, priority, "unrelated mixed sources");

        assertVanillaControls(player);
        player.setItemSlot(
                EquipmentSlot.FEET, item("betterend", "aeternium_boots").getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Adapter made unrelated Better End boots piglin-safe");
        require(BetterNetherSpikeProbe.boundaryFailures() == 0,
                "Adapter encountered a Better Nether reflection failure");
        helper.succeed();
    }

    private static void assertMixedResult(
            Player player,
            ItemStack mixed,
            boolean expected,
            String priority,
            String description) {
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        BetterNetherSpikeProbe.resetCounters();
        require(PiglinAi.isWearingSafeArmor(player) == expected,
                description + " produced the wrong piglin-safe result");
        int selectedEvaluations = "high".equals(priority)
                ? BetterNetherSpikeProbe.highEvaluations()
                : BetterNetherSpikeProbe.lowEvaluations();
        int unselectedEvaluations = "high".equals(priority)
                ? BetterNetherSpikeProbe.lowEvaluations()
                : BetterNetherSpikeProbe.highEvaluations();
        require(selectedEvaluations == 1,
                description + " did not evaluate the selected adapter exactly once");
        require(unselectedEvaluations == 0,
                description + " also evaluated the unselected adapter");
        require(BetterNetherSpikeProbe.claims() == (expected ? 1 : 0),
                description + " produced a duplicate or missing adapter claim");
        require(BetterNetherSpikeProbe.boundaryFailures() == 0,
                description + " encountered a Better Nether boundary failure");
    }

    private static void assertVanillaControls(Player player) {
        BetterNetherSpikeProbe.resetCounters();
        player.setItemSlot(EquipmentSlot.FEET, Items.GOLDEN_BOOTS.getDefaultInstance());
        require(PiglinAi.isWearingSafeArmor(player),
                "Adapter changed vanilla golden-boots safety");
        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Adapter made vanilla iron boots piglin-safe");
    }

    private static boolean requiredBooleanProperty(String name) {
        String value = System.getProperty(name);
        require("true".equals(value) || "false".equals(value),
                "Missing expected boolean property " + name);
        return Boolean.parseBoolean(value);
    }

    private static Item item(String namespace, String path) {
        Identifier identifier = Identifier.fromNamespaceAndPath(namespace, path);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        require(identifier.equals(BuiltInRegistries.ITEM.getKey(item)),
                "Missing exact BetterX item " + identifier);
        return item;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
