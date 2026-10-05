package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.gametest.TestContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;

/** Package-local composition entry points let tests select rules without a production config API. */
public final class ComponentCompositionGameTests {
    @GameTest(structure = "mismatchedboots-test:empty")
    public void numericFailuresRejectWholeOperationsWithoutMutations(GameTestHelper helper) {
        var entries = new LinkedHashMap<>(ComponentRuleDefaults.rules());
        entries.put(TestContent.NUMERIC_COMPONENT_ID, ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT));
        var rules = new ComponentRuleSet(entries);
        var type = TestContent.NUMERIC_COMPONENT;
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        left.set(type, 2);
        right.set(type, 6);
        var pair = create(left, right, rules);
        helper.assertValueEqual(pair.primary().get(type), 4, "valid primary mean");
        helper.assertValueEqual(pair.inverse().get(type), 4, "valid inverse mean");
        ItemStack root = pair.primary();
        ItemStack before = root.copy();
        right.set(type, 4);
        pair = create(left, right, rules);
        helper.assertValueEqual(pair.primary().get(type), 2, "invalid result primary owner fallback");
        helper.assertValueEqual(pair.inverse().get(type), 4, "invalid result inverse owner fallback");

        left.set(type, -2);
        right.set(type, 12);
        rejects(helper, () -> create(left, right, rules));
        rejects(helper, () -> create(right, left, rules));
        right.remove(type);
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        rejects(helper, () -> create(left, right, rules));
        rejects(helper, () -> create(right, left, rules));
        rejects(helper, () -> MixedBootsRecomposer.recompose(root, left, right,
                ModContent.mixedBootsDataType().orElseThrow(), AttributeRuleDefaults.ruleSet(), withComponents(rules)));
        helper.assertTrue(ItemStack.matches(before, root), "Rejected recomposition mutated live root");
        helper.assertTrue(ItemStack.matches(leftBefore, left) && ItemStack.matches(rightBefore, right),
                "Rejected composition changed or consumed inputs");
        helper.succeed();
    }

    private static MixedBootsPair create(ItemStack left, ItemStack right, ComponentRuleSet rules) {
        return MixedBootsFactory.createPair(left, right, BuiltInRegistries.ITEM.getOrThrow(ModContent.MIXED_BOOTS_KEY),
                ModContent.mixedBootsDataType().orElseThrow(), AttributeRuleDefaults.ruleSet(), withComponents(rules));
    }

    private static ComponentRules withComponents(ComponentRuleSet components) {
        ComponentRules defaults = ComponentRuleDefaults.state();
        return new ComponentRules(defaults.durability(), defaults.repairCost(), components);
    }

    private static void rejects(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail("Codec-invalid numeric source was accepted");
    }
}
