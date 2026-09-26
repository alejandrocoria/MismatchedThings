package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ComponentRulesConfigGameTests {
    private static final String CUSTOM_RULES_PROPERTY = "mismatchedboots.test.componentRules";
    private static final Identifier DORMANT_ID = Identifier.parse("futuremod:dormant_energy");

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void configuredCompositionWorks(GameTestHelper helper) {
        if (!Boolean.getBoolean(CUSTOM_RULES_PROPERTY)) {
            helper.succeed();
            return;
        }

        var rules = ComponentRuleState.current();
        helper.assertValueEqual(rules.durability(), DurabilityPolicy.SEPARATE, "loaded durability policy");
        helper.assertValueEqual(rules.repairCost(), RepairCostPolicy.OWNER, "loaded repair-cost policy");
        helper.assertValueEqual(rules.declarations().get(TestContent.NUMERIC_COMPONENT_ID),
                ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT),
                "loaded numeric declaration");
        helper.assertValueEqual(rules.declarations().get(DORMANT_ID),
                ComponentRule.of(ComponentPolicy.SHARE), "retained dormant declaration");
        helper.assertTrue(!rules.components().rules().containsKey(DORMANT_ID),
                "Dormant declaration became an effective rule");

        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        left.setDamageValue(11);
        right.setDamageValue(7);
        left.set(DataComponents.REPAIR_COST, 2);
        right.set(DataComponents.REPAIR_COST, 5);
        left.set(TestContent.requireNumericComponent(), 2);
        right.set(TestContent.requireNumericComponent(), 6);

        var pair = GameTestStacks.pair(left, right);
        helper.assertValueEqual(pair.primary().getMaxDamage(), left.getMaxDamage(), "primary separate maximum");
        helper.assertValueEqual(pair.primary().getDamageValue(), 11, "primary separate damage");
        helper.assertValueEqual(pair.inverse().getMaxDamage(), right.getMaxDamage(), "inverse separate maximum");
        helper.assertValueEqual(pair.inverse().getDamageValue(), 7, "inverse separate damage");
        helper.assertValueEqual(pair.primary().getOrDefault(DataComponents.REPAIR_COST, 0), 2,
                "primary owner repair cost");
        helper.assertValueEqual(pair.inverse().getOrDefault(DataComponents.REPAIR_COST, 0), 5,
                "inverse owner repair cost");
        for (ItemStack output : List.of(pair.primary(), pair.inverse())) {
            helper.assertValueEqual(output.get(TestContent.requireNumericComponent()), 4,
                    "configured numeric average");
        }
        helper.succeed();
    }
}
