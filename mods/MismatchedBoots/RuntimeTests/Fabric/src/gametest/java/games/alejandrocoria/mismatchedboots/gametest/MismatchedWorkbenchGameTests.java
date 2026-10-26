package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("removal")
public final class MismatchedWorkbenchGameTests {
    private static final BlockPos WORKBENCH_POS = BlockPos.ZERO;
    private static final ResourceKey<Recipe<?>> TEST_RECIPE = ResourceKey.create(
            Registries.RECIPE,
            ModContent.MIXED_BOOTS_WORKBENCH_RECIPE_ID);

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void previewTracksOrderedInputsAndInventoryRouting(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu menu = menu(helper, player, 1);

        menu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                          "One input created a partial preview");
        menu.getSlot(1).set(new ItemStack(Items.GOLDEN_BOOTS));
        assertPair(helper, menu, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "initial preview");
        assertUncommitted(helper, player, menu, "initial preview");

        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_BOOTS));
        assertPair(helper, menu, Items.DIAMOND_BOOTS, Items.GOLDEN_BOOTS, "replacement preview");
        menu.getSlot(1).set(ItemStack.EMPTY);
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                          "Clearing an input did not clear both previews");

        menu.getSlot(0).set(new ItemStack(Items.GOLDEN_BOOTS));
        menu.getSlot(1).set(new ItemStack(Items.IRON_BOOTS));
        assertPair(helper, menu, Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "reversed preview");

        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.IRON_BOOTS));
        player.getInventory().setItem(0, new ItemStack(Items.GOLDEN_BOOTS));
        helper.assertTrue(!menu.quickMoveStack(player, MismatchedWorkbenchMenu.PLAYER_INVENTORY_START).isEmpty(),
                          "Shift-click did not route a source from the main inventory");
        helper.assertTrue(!menu.quickMoveStack(player, MismatchedWorkbenchMenu.HOTBAR_START).isEmpty(),
                          "Shift-click did not route a source from the hotbar");
        assertPair(helper, menu, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "routed preview");
        helper.assertTrue(!menu.quickMoveStack(player, 0).isEmpty(),
                          "Shift-click did not return an uncommitted input");
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                          "Returning an input did not clear both previews");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void everyExtractionPathCommitsOnlyAfterSuccess(GameTestHelper helper) {
        assertFirstExtraction(helper, ContainerInput.PICKUP, 0, 2, "normal pickup");
        assertFirstExtraction(helper, ContainerInput.PICKUP, 1, 3, "right-click pickup");
        assertFirstExtraction(helper, ContainerInput.SWAP, 0, 2, "number-key swap");
        assertFirstExtraction(helper, ContainerInput.THROW, 0, 3, "drop");
        assertFirstExtraction(helper, ContainerInput.QUICK_MOVE, 0, 2, "shift-click");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void secondOutputCompletesFrozenTransactionOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu menu = readyMenu(helper, player, 20);
        Item outputItem = menu.getSlot(2).getItem().getItem();

        ItemStack primary = menu.getSlot(2).safeTake(1, 1, player);
        GameTestStacks.assertOrientation(helper, primary, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "primary output");
        helper.assertTrue(menu.isCommitted(), "First output did not commit the transaction");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                          "First output did not consume both inputs");
        GameTestStacks.assertOrientation(helper, menu.getSlot(3).getItem(),
                                         Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "frozen inverse output");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND_BOOTS))
                                  && !menu.getSlot(0).mayPickup(player)
                                  && !menu.getSlot(0).isActive(),
                          "Committed input slot remained interactive");

        ItemStack inverse = menu.getSlot(3).safeTake(1, 1, player);
        GameTestStacks.assertOrientation(helper, inverse, Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "inverse output");
        helper.assertTrue(!menu.isCommitted(), "Second output did not finish the transaction");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEST_RECIPE), 1, "recipe trigger count");
        helper.assertValueEqual(craftedCount(player, outputItem), 2, "crafted callback count");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void fullInventoryLeavesPreviewAndInputsUntouched(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().getNonEquipmentItems().replaceAll(ignored -> new ItemStack(Items.STONE, 64));
        MismatchedWorkbenchMenu menu = readyMenu(helper, player, 30);

        ItemStack moved = menu.quickMoveStack(player, 2);

        helper.assertTrue(moved.isEmpty(), "Shift-click moved an output into a full inventory");
        helper.assertTrue(!menu.isCommitted(), "Failed shift-click committed the transaction");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_BOOTS)
                                  && menu.getSlot(1).getItem().is(Items.GOLDEN_BOOTS),
                          "Failed shift-click mutated inputs");
        assertPair(helper, menu, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "blocked preview");
        assertUncommitted(helper, player, menu, "blocked shift-click");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void closingReturnsOnlyRealTransactionItems(GameTestHelper helper) {
        ServerPlayer previewPlayer = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu preview = readyMenu(helper, previewPlayer, 40);
        preview.removed(previewPlayer);
        helper.assertValueEqual(count(previewPlayer, Items.IRON_BOOTS), 1, "returned iron input");
        helper.assertValueEqual(count(previewPlayer, Items.GOLDEN_BOOTS), 1, "returned gold input");
        helper.assertValueEqual(countMixed(previewPlayer), 0, "synthetic previews returned on close");
        helper.assertValueEqual(RecipeCraftedProbe.count(previewPlayer, TEST_RECIPE), 0,
                                "preview close recipe trigger");

        ServerPlayer committedPlayer = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu committed = readyMenu(helper, committedPlayer, 41);
        Item outputItem = committed.getSlot(2).getItem().getItem();
        committed.clicked(2, 0, ContainerInput.PICKUP, committedPlayer);
        helper.assertTrue(!committed.getCarried().isEmpty(), "Pickup did not carry the first output");
        committed.removed(committedPlayer);
        helper.assertTrue(committed.getCarried().isEmpty(), "Close retained the carried output");
        helper.assertValueEqual(countMixed(committedPlayer), 2, "committed outputs returned on close");
        helper.assertValueEqual(count(committedPlayer, Items.IRON_BOOTS), 0, "consumed iron input returned");
        helper.assertValueEqual(count(committedPlayer, Items.GOLDEN_BOOTS), 0, "consumed gold input returned");
        helper.assertValueEqual(RecipeCraftedProbe.count(committedPlayer, TEST_RECIPE), 1,
                                "committed close recipe trigger");
        helper.assertValueEqual(craftedCount(committedPlayer, outputItem), 1,
                                "unclaimed output received a crafted callback during close");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void blockCreatesIndependentMenusAndInvalidationCleansUp(GameTestHelper helper) {
        var block = ModContent.mismatchedWorkbenchBlock().orElseThrow();
        helper.setBlock(WORKBENCH_POS, block);
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();

        helper.useBlock(WORKBENCH_POS, first);
        helper.useBlock(WORKBENCH_POS, second);
        helper.assertTrue(first.containerMenu instanceof MismatchedWorkbenchMenu,
                          "Block did not open the registered workbench menu for the first player");
        helper.assertTrue(second.containerMenu instanceof MismatchedWorkbenchMenu,
                          "Block did not open the registered workbench menu for the second player");
        MismatchedWorkbenchMenu firstMenu = (MismatchedWorkbenchMenu) first.containerMenu;
        MismatchedWorkbenchMenu secondMenu = (MismatchedWorkbenchMenu) second.containerMenu;
        firstMenu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        firstMenu.getSlot(1).set(new ItemStack(Items.GOLDEN_BOOTS));
        helper.assertTrue(secondMenu.getSlot(0).getItem().isEmpty()
                                  && secondMenu.getSlot(1).getItem().isEmpty()
                                  && secondMenu.getSlot(2).getItem().isEmpty(),
                          "Two viewers shared workbench contents");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(WORKBENCH_POS)) == null,
                          "Mismatched Workbench unexpectedly created a block entity");

        helper.setBlock(WORKBENCH_POS, Blocks.AIR);
        helper.assertTrue(!firstMenu.stillValid(first), "Removed workbench left its menu valid");
        firstMenu.removed(first);
        helper.assertValueEqual(count(first, Items.IRON_BOOTS), 1, "invalidation returned iron input");
        helper.assertValueEqual(count(first, Items.GOLDEN_BOOTS), 1, "invalidation returned gold input");
        helper.assertValueEqual(countMixed(first), 0, "invalidation returned synthetic previews");
        secondMenu.removed(second);
        helper.succeed();
    }

    private static void assertFirstExtraction(
            GameTestHelper helper, ContainerInput action, int button, int resultSlot, String description) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu menu = readyMenu(helper, player, 10 + resultSlot + button);
        Item outputItem = menu.getSlot(resultSlot).getItem().getItem();

        if (action == ContainerInput.QUICK_MOVE) {
            helper.assertTrue(!menu.quickMoveStack(player, resultSlot).isEmpty(),
                              description + " did not extract the output");
        } else {
            menu.clicked(resultSlot, button, action, player);
        }

        helper.assertTrue(menu.isCommitted(), description + " did not commit");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                          description + " did not consume both inputs");
        helper.assertTrue(menu.getSlot(resultSlot).getItem().isEmpty(),
                          description + " left the extracted output in place");
        helper.assertTrue(!menu.getSlot(resultSlot == 2 ? 3 : 2).getItem().isEmpty(),
                          description + " removed the frozen counterpart");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEST_RECIPE), 1,
                                description + " recipe trigger count");
        helper.assertValueEqual(craftedCount(player, outputItem), 1,
                                description + " crafted callback count");
    }

    private static MismatchedWorkbenchMenu readyMenu(GameTestHelper helper, ServerPlayer player, int id) {
        MismatchedWorkbenchMenu menu = menu(helper, player, id);
        menu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        menu.getSlot(1).set(new ItemStack(Items.GOLDEN_BOOTS));
        assertPair(helper, menu, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "ready preview");
        return menu;
    }

    private static MismatchedWorkbenchMenu menu(GameTestHelper helper, ServerPlayer player, int id) {
        var block = ModContent.mismatchedWorkbenchBlock().orElseThrow();
        helper.setBlock(WORKBENCH_POS, block);
        return new MismatchedWorkbenchMenu(id, player.getInventory(), ContainerLevelAccess.create(
                helper.getLevel(), helper.absolutePos(WORKBENCH_POS)));
    }

    private static void assertPair(
            GameTestHelper helper,
            MismatchedWorkbenchMenu menu,
            Item left,
            Item right,
            String description) {
        GameTestStacks.assertOrientation(helper, menu.getSlot(2).getItem(), left, right,
                                         description + " primary");
        GameTestStacks.assertOrientation(helper, menu.getSlot(3).getItem(), right, left,
                                         description + " inverse");
    }

    private static void assertUncommitted(
            GameTestHelper helper, ServerPlayer player, MismatchedWorkbenchMenu menu, String description) {
        helper.assertTrue(!menu.isCommitted(), description + " unexpectedly committed");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEST_RECIPE), 0,
                                description + " recipe trigger count");
        helper.assertValueEqual(craftedCount(player, menu.getSlot(2).getItem().getItem()), 0,
                                description + " crafted callback count");
    }

    private static int craftedCount(ServerPlayer player, Item item) {
        return player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                     .filter(stack -> stack.is(item))
                     .mapToInt(ItemStack::getCount)
                     .sum();
    }

    private static int countMixed(ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems().stream()
                     .filter(ModContent::isMixedBoots)
                     .mapToInt(ItemStack::getCount)
                     .sum();
    }
}
