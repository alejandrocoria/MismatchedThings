package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CraftingGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void directCompositionRecipeIsAbsentFromEveryRepresentativeLayout(GameTestHelper helper) {
        int[][] playerLayouts = {{0, 1}, {0, 2}, {0, 3}, {1, 2}};
        for (int[] layout : playerLayouts) {
            assertNoRecipe(helper, 2, 2, layout[0], layout[1], Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
            assertNoRecipe(helper, 2, 2, layout[0], layout[1], Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        }

        int[][] tableLayouts = {{0, 1}, {0, 3}, {0, 4}, {0, 8}, {1, 7}, {2, 6}};
        for (int[] layout : tableLayouts) {
            assertNoRecipe(helper, 3, 3, layout[0], layout[1], Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
            assertNoRecipe(helper, 3, 3, layout[0], layout[1], Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void ordinaryCraftingNeverPreviewsOrShiftClicksMixedBoots(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        InventoryMenu inventoryMenu = new InventoryMenu(player.getInventory(), true, player);
        assertNoMenuComposition(helper, player, inventoryMenu, 0, 3, "2x2 inventory");

        CraftingMenu tableMenu = new CraftingMenu(
                1,
                player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
        assertNoMenuComposition(helper, player, tableMenu, 0, 8, "3x3 crafting table");

        helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().noneMatch(ModContent::isMixedBoots),
                "Ordinary crafting inserted mixed boots into the player inventory");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void vanillaRepairRecipeCurrentlyProducesBareMixedBoots(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack first = pair.primary();
        ItemStack second = pair.inverse();
        first.setDamageValue(Math.max(1, first.getMaxDamage() / 2));
        second.setDamageValue(Math.max(1, second.getMaxDamage() / 2));

        CraftingInput input = input(2, 2, 0, first, 3, second);
        var recipe = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("Vanilla repair recipe did not accept two mixed stacks"));
        helper.assertTrue(recipe.value() instanceof RepairItemRecipe,
                "Two mixed stacks unexpectedly selected a non-vanilla repair recipe: " + recipe);

        ItemStack output = recipe.value().assemble(input);
        helper.assertTrue(output.is(ModContent.MIXED_BOOTS_KEY),
                "Vanilla repair did not retain the mixed boots item identity");
        helper.assertTrue(ModContent.getMixedBootsData(output).isEmpty(),
                "Characterization changed: vanilla repair unexpectedly retained mixed boots data");
        helper.succeed();
    }

    private static void assertNoRecipe(
            GameTestHelper helper,
            int width,
            int height,
            int firstSlot,
            int secondSlot,
            Item first,
            Item second) {
        CraftingInput input = input(width, height, firstSlot, new ItemStack(first), secondSlot, new ItemStack(second));
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isEmpty(),
                "Direct composition recipe remained for " + width + "x" + height
                        + " layout " + firstSlot + "/" + secondSlot
                        + " and order " + first + "/" + second + ": " + recipe);
    }

    private static void assertNoMenuComposition(
            GameTestHelper helper,
            ServerPlayer player,
            AbstractCraftingMenu menu,
            int firstSlot,
            int secondSlot,
            String description) {
        menu.getInputGridSlots().get(firstSlot).set(new ItemStack(Items.IRON_BOOTS));
        menu.getInputGridSlots().get(secondSlot).set(new ItemStack(Items.GOLDEN_BOOTS));

        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(),
                description + " exposed a legacy preview");
        ItemStack moved = menu.quickMoveStack(player, 0);
        helper.assertTrue(moved.isEmpty(), description + " shift-click created a legacy result");
        helper.assertTrue(menu.getInputGridSlots().get(firstSlot).getItem().is(Items.IRON_BOOTS),
                description + " shift-click consumed the first input");
        helper.assertTrue(menu.getInputGridSlots().get(secondSlot).getItem().is(Items.GOLDEN_BOOTS),
                description + " shift-click consumed the second input");
        helper.assertValueEqual(menu.getInputGridSlots().stream()
                .filter(slot -> !slot.getItem().isEmpty()).count(), 2L,
                description + " occupied input count");
    }

    private static CraftingInput input(int width, int height, Object... slotAndStacks) {
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(width * height, ItemStack.EMPTY));
        for (int index = 0; index < slotAndStacks.length; index += 2) {
            stacks.set((int) slotAndStacks[index], (ItemStack) slotAndStacks[index + 1]);
        }
        return CraftingInput.of(width, height, stacks);
    }
}
