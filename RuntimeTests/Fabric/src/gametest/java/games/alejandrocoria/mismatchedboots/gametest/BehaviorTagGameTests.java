package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

public final class BehaviorTagGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void projectsPiglinSafetyFromEitherSource(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.GOLDEN_BOOTS, Items.IRON_BOOTS);

        helper.assertTrue(pair.primary().is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Primary output did not project piglin safety from its left source");
        helper.assertTrue(pair.inverse().is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Inverse output did not project piglin safety from its right source");
        helper.assertTrue(!GameTestStacks.mixed(Items.IRON_BOOTS, Items.DIAMOND_BOOTS)
                        .is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Non-golden sources unexpectedly projected piglin safety");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void preservesOrdinaryAndUnauditedTagAnswers(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(Items.GOLDEN_BOOTS).is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Vanilla golden boots lost their positive piglin-safety result");
        helper.assertTrue(!new ItemStack(Items.IRON_BOOTS).is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Vanilla iron boots gained piglin safety");

        var armorPair = GameTestStacks.pair(Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        for (ItemStack output : List.of(armorPair.primary(), armorPair.inverse())) {
            helper.assertTrue(!output.is(ItemTags.FOOT_ARMOR),
                    "Mixed boots inherited the unaudited foot-armor tag");
            helper.assertTrue(!output.is(ItemTags.TRIMMABLE_ARMOR),
                    "Mixed boots inherited the unaudited trimmable-armor tag");
        }

        ItemStack ironMaterial = new ItemStack(Items.IRON_INGOT);
        ironMaterial.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        helper.assertTrue(ironMaterial.is(ItemTags.REPAIRS_IRON_ARMOR),
                "Repair-tag fixture is not a member of repairs_iron_armor");
        helper.assertTrue(ironMaterial.is(ItemTags.IRON_TOOL_MATERIALS),
                "Tier-tag fixture is not a member of iron_tool_materials");
        ItemStack materialMixed = GameTestStacks.pair(ironMaterial, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        helper.assertTrue(!materialMixed.is(ItemTags.REPAIRS_IRON_ARMOR),
                "Mixed boots inherited an unaudited repair tag");
        helper.assertTrue(!materialMixed.is(ItemTags.IRON_TOOL_MATERIALS),
                "Mixed boots inherited an unaudited tier tag");

        ItemStack recipeMaterial = new ItemStack(Items.COBBLESTONE);
        recipeMaterial.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        helper.assertTrue(recipeMaterial.is(ItemTags.STONE_CRAFTING_MATERIALS),
                "Recipe-tag fixture is not a member of stone_crafting_materials");
        ItemStack recipeMixed = GameTestStacks.pair(
                recipeMaterial, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        helper.assertTrue(!recipeMixed.is(ItemTags.STONE_CRAFTING_MATERIALS),
                "Mixed boots inherited an unaudited recipe tag");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void bareAndInvalidRegisteredStacksRemainFalse(GameTestHelper helper) {
        ItemStack valid = GameTestStacks.mixed(Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        ItemStack bare = valid.copy();
        bare.remove(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack invalid = valid.copy();
        invalid.setCount(2);

        helper.assertTrue(!bare.is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Bare registered mixed boots projected piglin safety");
        helper.assertTrue(!invalid.is(ItemTags.PIGLIN_SAFE_ARMOR),
                "Invalid registered mixed boots projected piglin safety");
        helper.succeed();
    }
}
