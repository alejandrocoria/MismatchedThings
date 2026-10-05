package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class AttributeRulesGameTests {
    private static final String CUSTOM_RULES_PROPERTY = "mismatchedboots.test.attributeRules";
    private static final Identifier SHARED_ID =
            Identifier.fromNamespaceAndPath("mismatchedboots-test", "runtime_rule");

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void oneSidedAveragedIdentityIsDuplicatedAtHalfValue(GameTestHelper helper) {
        ItemStack first = bootsWithModifier(Items.IRON_BOOTS, Attributes.ARMOR, 4.0D);
        ItemStack second = new ItemStack(Items.GOLDEN_BOOTS);
        second.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        MixedBootsPair pair = GameTestStacks.pair(first, second);

        assertOnlyAmount(helper, pair.primary(), Attributes.ARMOR, 2.0D, "one-sided primary");
        assertOnlyAmount(helper, pair.inverse(), Attributes.ARMOR, 2.0D, "one-sided inverse");
        helper.assertValueEqual(modifiers(first).modifiers().getFirst().modifier().amount(), 4.0D,
                "one-sided source amount");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void invalidNumericInputMakesWorkbenchCompositionFailAtomically(GameTestHelper helper) {
        ItemStack invalid = bootsWithModifier(Items.IRON_BOOTS, Attributes.ARMOR, Double.NaN);
        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(
                invalid, new ItemStack(Items.GOLDEN_BOOTS));
        MixedBootsWorkbenchRecipe recipe = new MixedBootsWorkbenchRecipe();

        helper.assertTrue(recipe.matches(input, helper.getLevel()),
                "Structurally valid non-finite sources did not reach composition");
        var result = recipe.assemblePair(input);
        helper.assertTrue(!result.isComplete()
                        && result.primary().isEmpty()
                        && result.inverse().isEmpty(),
                "Non-finite workbench composition exposed a partial result");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void isolatedServerConfigControlsNewCompositionAndRejectsConfiguredAttributes(GameTestHelper helper) {
        if (!Boolean.getBoolean(CUSTOM_RULES_PROPERTY)) {
            helper.succeed();
            return;
        }

        helper.assertValueEqual(AttributeRulesLifecycle.serverRules().policyFor(Identifier.withDefaultNamespace("movement_speed")),
                AttributePolicy.COMBINE, "loaded movement-speed policy");
        helper.assertValueEqual(AttributeRulesLifecycle.serverRules().policyFor(
                        Identifier.withDefaultNamespace("waypoint_transmit_range")),
                AttributePolicy.REJECT, "loaded waypoint-transmit-range policy");

        ItemStack first = bootsWithModifier(Items.IRON_BOOTS, Attributes.MOVEMENT_SPEED, 0.2D);
        ItemStack second = bootsWithModifier(Items.GOLDEN_BOOTS, Attributes.MOVEMENT_SPEED, 0.4D);
        MixedBootsPair combined = GameTestStacks.pair(first, second);
        assertOnlyAmount(helper, combined.primary(), Attributes.MOVEMENT_SPEED, 0.6D, "configured primary");
        assertOnlyAmount(helper, combined.inverse(), Attributes.MOVEMENT_SPEED, 0.6D, "configured inverse");
        helper.assertValueEqual(modifiers(first).modifiers().getFirst().modifier().amount(), 0.2D,
                "configured first source amount");
        helper.assertValueEqual(modifiers(second).modifiers().getFirst().modifier().amount(), 0.4D,
                "configured second source amount");

        ItemStack rejected = bootsWithModifier(Items.IRON_BOOTS, Attributes.WAYPOINT_TRANSMIT_RANGE, 1.0D);
        MismatchedWorkbenchInput rejectedInput = new MismatchedWorkbenchInput(
                rejected, new ItemStack(Items.GOLDEN_BOOTS));
        MixedBootsWorkbenchRecipe recipe = new MixedBootsWorkbenchRecipe();
        helper.assertTrue(recipe.matches(rejectedInput, helper.getLevel()),
                "Structurally valid configured-reject sources did not reach composition");
        var rejectedResult = recipe.assemblePair(rejectedInput);
        helper.assertTrue(!rejectedResult.isComplete()
                        && rejectedResult.primary().isEmpty()
                        && rejectedResult.inverse().isEmpty(),
                "Configured reject exposed a partial workbench result");
        helper.succeed();
    }

    private static ItemStack bootsWithModifier(
            net.minecraft.world.item.Item item,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            double amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(attribute, new AttributeModifier(SHARED_ID, amount, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build());
        return stack;
    }

    private static ItemAttributeModifiers modifiers(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }

    private static void assertOnlyAmount(
            GameTestHelper helper,
            ItemStack stack,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            double expected,
            String description) {
        var entries = modifiers(stack).modifiers().stream()
                .filter(entry -> entry.attribute().equals(attribute))
                .toList();
        helper.assertValueEqual(entries.size(), 1, description + " entries");
        helper.assertValueEqual(entries.getFirst().modifier().id(), SHARED_ID, description + " modifier ID");
        helper.assertTrue(Math.abs(entries.getFirst().modifier().amount() - expected) < 1.0E-12D,
                description + " amount was " + entries.getFirst().modifier().amount());
    }
}
