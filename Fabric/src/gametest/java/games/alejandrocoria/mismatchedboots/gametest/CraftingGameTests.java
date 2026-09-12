package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CraftingGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void liveRecipeManagerFindsTheRecipe(GameTestHelper helper) {
        CraftingInput input = input(3, 3, 0, new ItemStack(Items.IRON_BOOTS), 8, new ItemStack(Items.GOLDEN_BOOTS));
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

        helper.assertTrue(recipe.isPresent(), "Live recipe manager did not find the mixed boots recipe");
        helper.assertTrue(recipe.orElseThrow().id().identifier().equals(ModContent.MIXED_BOOTS_RECIPE_ID),
                "Live recipe manager selected an unexpected recipe: " + recipe.orElseThrow().id());
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void normalTakeWorksInPlayerGridAndCraftingTable(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        InventoryMenu inventoryMenu = new InventoryMenu(player.getInventory(), true, player);
        place(inventoryMenu, 0, Items.IRON_BOOTS, 3, Items.GOLDEN_BOOTS);
        ItemStack inventoryResult = inventoryMenu.getSlot(0).safeTake(1, 1, player);
        assertTransaction(helper, inventoryMenu, inventoryResult, 0);

        CraftingMenu tableMenu = new CraftingMenu(
                1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
        place(tableMenu, 2, Items.IRON_BOOTS, 7, Items.GOLDEN_BOOTS);
        ItemStack tableResult = tableMenu.getSlot(0).safeTake(1, 1, player);
        assertTransaction(helper, tableMenu, tableResult, 2);
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void shiftClickMovesPrimaryAndLeavesInverse(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CraftingMenu menu = craftingMenu(helper, player);
        place(menu, 0, Items.IRON_BOOTS, 8, Items.GOLDEN_BOOTS);

        ItemStack moved = menu.quickMoveStack(player, 0);

        GameTestStacks.assertOrientation(helper, moved, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "shift-click result");
        GameTestStacks.assertOrientation(helper, menu.getInputGridSlots().get(0).getItem(),
                Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "shift-click remainder");
        List<ItemStack> inventoryOutputs = player.getInventory().getNonEquipmentItems().stream()
                .filter(ModContent::isMixedBoots)
                .toList();
        helper.assertValueEqual(inventoryOutputs.size(), 1, "mixed boots in player inventory");
        GameTestStacks.assertOrientation(helper, inventoryOutputs.getFirst(),
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "shift-click inventory result");
        assertOnlyInverseRemains(helper, menu, 0);
        assertNoDroppedItems(helper, "shift-click craft");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void fullInventoryBlocksShiftClickWithoutConsumingInputs(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().getNonEquipmentItems().replaceAll(ignored -> new ItemStack(Items.STONE, 64));

        CraftingMenu normalMenu = craftingMenu(helper, player);
        place(normalMenu, 0, Items.IRON_BOOTS, 8, Items.GOLDEN_BOOTS);
        ItemStack taken = normalMenu.getSlot(0).safeTake(1, 1, player);
        assertTransaction(helper, normalMenu, taken, 0);

        CraftingMenu menu = craftingMenu(helper, player);
        place(menu, 0, Items.IRON_BOOTS, 8, Items.GOLDEN_BOOTS);

        ItemStack moved = menu.quickMoveStack(player, 0);

        helper.assertTrue(moved.isEmpty(), "Shift-click unexpectedly crafted into a full inventory");
        helper.assertTrue(menu.getInputGridSlots().get(0).getItem().is(Items.IRON_BOOTS), "First input was consumed");
        helper.assertTrue(menu.getInputGridSlots().get(8).getItem().is(Items.GOLDEN_BOOTS), "Second input was consumed");
        GameTestStacks.assertOrientation(helper, menu.getSlot(0).getItem(),
                Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "unchanged preview");
        helper.assertValueEqual(menu.getInputGridSlots().stream().filter(slot -> !slot.getItem().isEmpty()).count(),
                2L, "occupied input slots after blocked shift-click");
        helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().noneMatch(ModContent::isMixedBoots),
                "Blocked shift-click inserted mixed boots into the full inventory");
        assertNoDroppedItems(helper, "blocked shift-click craft");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void previewAndRepresentativeLayoutsPreserveReadingOrder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[][] playerLayouts = {{0, 1}, {0, 2}, {0, 3}, {1, 2}};
        for (int[] layout : playerLayouts) {
            InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);
            assertPreview(helper, menu, layout, Items.IRON_BOOTS, Items.GOLDEN_BOOTS);

            InventoryMenu reversed = new InventoryMenu(player.getInventory(), true, player);
            assertPreview(helper, reversed, layout, Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        }

        int[][] layouts = {{0, 3}, {1, 2}, {0, 8}, {2, 6}, {1, 7}};
        for (int[] layout : layouts) {
            CraftingMenu menu = craftingMenu(helper, player);
            assertPreview(helper, menu, layout, Items.IRON_BOOTS, Items.GOLDEN_BOOTS);

            CraftingMenu reversed = craftingMenu(helper, player);
            assertPreview(helper, reversed, layout, Items.GOLDEN_BOOTS, Items.IRON_BOOTS);
        }
        helper.succeed();
    }

    private static void assertPreview(
            GameTestHelper helper,
            AbstractCraftingMenu menu,
            int[] layout,
            net.minecraft.world.item.Item first,
            net.minecraft.world.item.Item second) {
        place(menu, layout[0], first, layout[1], second);
        String description = "preview " + layout[0] + "/" + layout[1];
        GameTestStacks.assertOrientation(helper, menu.getSlot(0).getItem(), first, second, description);
        helper.assertTrue(menu.getInputGridSlots().get(layout[0]).getItem().is(first),
                "Preview consumed the first input in " + description);
        helper.assertTrue(menu.getInputGridSlots().get(layout[1]).getItem().is(second),
                "Preview consumed the second input in " + description);
    }

    private static void place(AbstractCraftingMenu menu, int firstSlot, net.minecraft.world.item.Item first,
                              int secondSlot, net.minecraft.world.item.Item second) {
        menu.getInputGridSlots().get(firstSlot).set(new ItemStack(first));
        menu.getInputGridSlots().get(secondSlot).set(new ItemStack(second));
    }

    private static CraftingMenu craftingMenu(GameTestHelper helper, ServerPlayer player) {
        return new CraftingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
    }

    private static void assertTransaction(
            GameTestHelper helper, AbstractCraftingMenu menu, ItemStack primary, int remainderSlot) {
        GameTestStacks.assertOrientation(helper, primary, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "primary result");
        helper.assertValueEqual(primary.getCount(), 1, "primary result count");
        assertOnlyInverseRemains(helper, menu, remainderSlot);
        assertNoDroppedItems(helper, "normal craft");
    }

    private static void assertOnlyInverseRemains(
            GameTestHelper helper, AbstractCraftingMenu menu, int remainderSlot) {
        List<ItemStack> remaining = menu.getInputGridSlots().stream()
                .map(slot -> slot.getItem())
                .filter(stack -> !stack.isEmpty())
                .toList();
        helper.assertValueEqual(remaining.size(), 1, "non-empty input slots after craft");
        ItemStack inverse = menu.getInputGridSlots().get(remainderSlot).getItem();
        GameTestStacks.assertOrientation(helper, inverse,
                Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "inverse remainder");
        helper.assertValueEqual(inverse.getCount(), 1, "inverse remainder count");
    }

    private static void assertNoDroppedItems(GameTestHelper helper, String operation) {
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class, helper.getBounds(), Entity::isAlive).isEmpty(),
                operation + " created an unexpected dropped item");
    }

    private static CraftingInput input(int width, int height, Object... slotAndStacks) {
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(width * height, ItemStack.EMPTY));
        for (int index = 0; index < slotAndStacks.length; index += 2) {
            stacks.set((int) slotAndStacks[index], (ItemStack) slotAndStacks[index + 1]);
        }
        return CraftingInput.of(width, height, stacks);
    }
}
