package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsRepairRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CraftingGameTests {
    private static final ResourceKey<Recipe<?>> MIXED_REPAIR_RECIPE = ResourceKey.create(
            Registries.RECIPE, ModContent.MIXED_BOOTS_REPAIR_RECIPE_ID);

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
    public void loadedRepairRecipeAcceptsEitherSourceAndEitherOrder(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack mixed = pair.primary();
        mixed.setDamageValue(Math.max(1, mixed.getMaxDamage() / 2));

        assertLoadedRepair(helper, mixed, new ItemStack(Items.IRON_BOOTS), 0, 3);
        assertLoadedRepair(helper, new ItemStack(Items.IRON_BOOTS), mixed, 0, 3);
        assertLoadedRepair(helper, mixed, new ItemStack(Items.GOLDEN_BOOTS), 1, 2);
        assertLoadedRepair(helper, new ItemStack(Items.GOLDEN_BOOTS), mixed, 1, 2);
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void mixedInputsCannotReachTheVanillaRepairRecipe(GameTestHelper helper) {
        var pair = GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        assertNoLoadedRecipe(helper, input(2, 2, 0, pair.primary(), 3, pair.inverse()),
                             "two mixed stacks");

        ItemStack bareMixed = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        assertNoLoadedRecipe(helper, input(2, 2, 0, bareMixed, 3, new ItemStack(Items.IRON_BOOTS)),
                             "bare mixed stack and ordinary donor");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void ordinaryCraftingRepairRemainsVanillaAndCompositionRemainsAbsent(GameTestHelper helper) {
        ItemStack firstIron = new ItemStack(Items.IRON_BOOTS);
        ItemStack secondIron = new ItemStack(Items.IRON_BOOTS);
        firstIron.setDamageValue(Math.max(1, firstIron.getMaxDamage() / 2));
        secondIron.setDamageValue(Math.max(1, secondIron.getMaxDamage() / 2));
        CraftingInput repairInput = input(2, 2, 0, firstIron, 3, secondIron);
        var repair = helper.getLevel().recipeAccess()
                           .getRecipeFor(RecipeType.CRAFTING, repairInput, helper.getLevel())
                           .orElseThrow(() -> new AssertionError("Ordinary vanilla repair recipe disappeared"));
        helper.assertTrue(repair.value() instanceof RepairItemRecipe,
                          "Ordinary repair selected a non-vanilla recipe: " + repair);
        helper.assertTrue(repair.value().assemble(repairInput).is(Items.IRON_BOOTS),
                          "Ordinary vanilla repair produced the wrong item");

        assertNoLoadedRecipe(helper, input(2, 2, 0, new ItemStack(Items.IRON_BOOTS),
                                           3, new ItemStack(Items.GOLDEN_BOOTS)), "two ordinary source boots");
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

    private static void assertLoadedRepair(
            GameTestHelper helper,
            ItemStack first,
            ItemStack second,
            int firstSlot,
            int secondSlot) {
        CraftingInput input = input(2, 2, firstSlot, first, secondSlot, second);
        var recipe = helper.getLevel().recipeAccess()
                           .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                           .orElseThrow(() -> new AssertionError("Mixed repair recipe did not load or match"));
        helper.assertTrue(recipe.id().equals(MIXED_REPAIR_RECIPE),
                          "Mixed repair selected the wrong recipe ID: " + recipe.id());
        helper.assertTrue(recipe.value() instanceof MixedBootsRepairRecipe,
                          "Mixed repair selected the wrong recipe implementation: " + recipe);
        ItemStack mixed = ModContent.isMixedBoots(first) ? first : second;
        ItemStack output = recipe.value().assemble(input);
        helper.assertTrue(output.is(ModContent.MIXED_BOOTS_KEY),
                          "Mixed repair did not retain the mixed item identity");
        helper.assertTrue(ModContent.getMixedBootsData(output).equals(ModContent.getMixedBootsData(mixed)),
                          "Mixed repair did not retain the exact source data");
    }

    private static void assertNoLoadedRecipe(GameTestHelper helper, CraftingInput input, String description) {
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isEmpty(), description + " unexpectedly selected a recipe: " + recipe);
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
