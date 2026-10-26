package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("removal")
public final class MismatchedWorkbenchContentGameTests {
    private static final String RELOAD_PROPERTY = "mismatchedboots.test.workbenchRecipeReload";
    private static final String DISABLING_PACK_NAME = "mismatchedboots-disable-data-recipes";
    private static final String DISABLING_PACK_ID = "file/" + DISABLING_PACK_NAME;
    private static final BlockPos WORKBENCH_POS = BlockPos.ZERO;
    private static final ResourceKey<Recipe<?>> BLOCK_RECIPE = ResourceKey.create(
            Registries.RECIPE, ModContent.MISMATCHED_WORKBENCH_ID);
    private static final ResourceKey<Recipe<?>> COMPOSITION_RECIPE = ResourceKey.create(
            Registries.RECIPE, ModContent.MIXED_BOOTS_WORKBENCH_RECIPE_ID);
    private static final ResourceKey<Recipe<?>> REPAIR_RECIPE = ResourceKey.create(
            Registries.RECIPE, ModContent.MIXED_BOOTS_REPAIR_RECIPE_ID);

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void shapedRecipeUsesSelectedPatternAndConsumesShears(GameTestHelper helper) {
        CraftingRecipe recipe = acquisitionRecipe(helper);
        CraftingInput mixedPlanks = workbenchInput(
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
                Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS);
        helper.assertTrue(recipe.matches(mixedPlanks, helper.getLevel()),
                          "Workbench recipe rejected mixed vanilla plank types");
        helper.assertTrue(recipe.assemble(mixedPlanks).is(ModContent.MISMATCHED_WORKBENCH_ITEM_KEY),
                          "Workbench recipe returned the wrong item");

        List<ItemStack> occupiedLowerCenter = new ArrayList<>(workbenchStacks(
                Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS,
                Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS));
        occupiedLowerCenter.set(7, new ItemStack(Items.STICK));
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, occupiedLowerCenter), helper.getLevel()),
                          "Workbench recipe accepted an occupied lower-center slot");
        List<ItemStack> missingShears = copyStacks(mixedPlanks);
        missingShears.set(1, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, missingShears), helper.getLevel()),
                          "Workbench recipe accepted missing shears");
        List<ItemStack> missingDiamond = copyStacks(mixedPlanks);
        missingDiamond.set(4, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, missingDiamond), helper.getLevel()),
                          "Workbench recipe accepted a missing diamond");
        List<ItemStack> changedOccupancy = copyStacks(mixedPlanks);
        changedOccupancy.set(0, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, changedOccupancy), helper.getLevel()),
                          "Workbench recipe accepted changed plank occupancy");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(WORKBENCH_POS, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(
                helper.getLevel(), helper.absolutePos(WORKBENCH_POS)));
        for (int slot = 0; slot < mixedPlanks.size(); slot++) {
            menu.getInputGridSlots().get(slot).set(mixedPlanks.getItem(slot).copy());
        }
        helper.assertTrue(menu.getInputGridSlots().get(7).getItem().isEmpty(),
                          "Lower-center slot was not empty before crafting");
        ItemStack crafted = menu.getSlot(CraftingMenu.RESULT_SLOT).safeTake(1, 1, player);
        helper.assertTrue(crafted.is(ModContent.MISMATCHED_WORKBENCH_ITEM_KEY),
                          "Crafting menu did not produce the workbench");
        helper.assertTrue(menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty()),
                          "Workbench crafting left an ingredient or returned the shears");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void blockItemPlacesOpensFreshSessionsAndDropsItself(GameTestHelper helper) {
        var block = ModContent.mismatchedWorkbenchBlock().orElseThrow();
        var item = ModContent.mismatchedWorkbenchItem().orElseThrow();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == block,
                          "Registered workbench item does not place the registered block");
        helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),
                          "Workbench is not mineable with an axe");
        helper.assertTrue(block.defaultBlockState().getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH,
                          "Workbench default state does not face north");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos support = helper.absolutePos(WORKBENCH_POS);
        BlockPos placed = support.east();
        player.setPos(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 2.5);
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        ItemStack placingStack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, placingStack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support), Direction.EAST, support, false);
        BlockPlaceContext placementContext = new BlockPlaceContext(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placementContext.canPlace(),
                          "Workbench placement context rejected the target position");
        var placementState = block.getStateForPlacement(placementContext);
        Direction expectedFacing = placementContext.getHorizontalDirection().getOpposite();
        helper.assertTrue(placementState != null
                                  && placementState.getValue(HorizontalDirectionalBlock.FACING) == expectedFacing,
                          "Workbench placement state does not face its placer");
        InteractionResult placement = placingStack.useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(helper.getLevel().getBlockState(placed).is(block),
                          "Workbench block item did not place the block: " + placement);
        helper.assertTrue(helper.getLevel().getBlockState(placed)
                                .getValue(HorizontalDirectionalBlock.FACING) == expectedFacing,
                          "Placed workbench does not face its placer");
        helper.assertTrue(helper.getLevel().getBlockEntity(placed) == null,
                          "Placed workbench unexpectedly created a block entity");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(WORKBENCH_POS.east(), player);
        helper.assertTrue(player.containerMenu instanceof MismatchedWorkbenchMenu,
                          "Placed workbench did not open its menu");
        MismatchedWorkbenchMenu first = (MismatchedWorkbenchMenu) player.containerMenu;
        first.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        player.closeContainer();
        helper.useBlock(WORKBENCH_POS.east(), player);
        helper.assertTrue(player.containerMenu instanceof MismatchedWorkbenchMenu second
                                  && second != first
                                  && second.getSlot(0).getItem().isEmpty(),
                          "Reopening the workbench reused an old session");
        player.closeContainer();

        helper.getLevel().destroyBlock(placed, true, player);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, helper.getBounds().inflate(2.0), Entity::isAlive);
        helper.assertValueEqual(drops.stream().filter(entity -> entity.getItem().is(item)).count(),
                                1L, "workbench self-drop count");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void directCraftingIsAbsentWhileWorkbenchCompositionRemains(GameTestHelper helper) {
        CraftingInput directInput = CraftingInput.of(2, 1, List.of(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
        helper.assertTrue(helper.getLevel().recipeAccess()
                                .getRecipeFor(RecipeType.CRAFTING, directInput, helper.getLevel()).isEmpty(),
                          "Crafting grid retained a direct mixed-boots recipe");

        RecipeHolder<MismatchedWorkbenchRecipe> station = helper.getLevel().recipeAccess()
                                                                .getRecipeFor(ModContent.mismatchedWorkbenchRecipeType(),
                                                                              new MismatchedWorkbenchInput(
                                                                                      new ItemStack(Items.IRON_BOOTS),
                                                                                      new ItemStack(Items.GOLDEN_BOOTS)),
                                                                              helper.getLevel())
                                                                .orElseThrow();
        var stationPair = station.value().assemblePair(new MismatchedWorkbenchInput(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
        helper.assertTrue(stationPair.isComplete(), "Workbench did not create both outputs");
        GameTestStacks.assertOrientation(helper, stationPair.primary(),
                                         Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "Workbench primary output");
        GameTestStacks.assertOrientation(helper, stationPair.inverse(),
                                         Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "Workbench inverse output");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void diamondUnlocksWorkbenchRecipeInTheVanillaRecipeBook(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!player.getRecipeBook().contains(BLOCK_RECIPE),
                          "Fresh player already knew the workbench recipe");

        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.inventoryMenu.broadcastChanges();

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(player.getRecipeBook().contains(BLOCK_RECIPE),
                              "Obtaining a diamond did not unlock the workbench recipe");
            helper.succeed();
        });
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE, maxTicks = 1200)
    public void recipePackRemovalDisablesOnlyDataDrivenTransformations(GameTestHelper helper) {
        if (!GameTestContexts.requireOrSkip(helper, RELOAD_PROPERTY, Boolean.getBoolean(RELOAD_PROPERTY))) {
            return;
        }

        MinecraftServer server = helper.getLevel().getServer();
        List<String> originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DISABLING_PACK_NAME);
        ServerPlayer pendingPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer shiftPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer committedPlayer = helper.makeMockServerPlayerInLevel();
        helper.setBlock(WORKBENCH_POS, ModContent.mismatchedWorkbenchBlock().orElseThrow());
        MismatchedWorkbenchMenu pending = readyMenu(helper, pendingPlayer, 11);
        MismatchedWorkbenchMenu shift = readyMenu(helper, shiftPlayer, 12);
        MismatchedWorkbenchMenu committed = readyMenu(helper, committedPlayer, 13);
        helper.assertTrue(!committed.getSlot(2).safeTake(1, 1, committedPlayer).isEmpty(),
                          "Could not commit a transaction before reload");
        ItemStack frozen = committed.getSlot(3).getItem().copy();
        try {
            writeDisablingPack(pack);
            server.getPackRepository().reload();
            helper.assertTrue(server.getPackRepository().isAvailable(DISABLING_PACK_ID),
                              "Composition-disabling test pack was not discovered");
            ArrayList<String> selected = new ArrayList<>(originalPacks);
            selected.add(DISABLING_PACK_ID);
            server.reloadResources(selected).join();

            pending.clicked(2, 0, ContainerInput.PICKUP, pendingPlayer);
            helper.assertTrue(pending.getCarried().isEmpty(), "Removed recipe's old preview was picked up");
            helper.assertTrue(shift.quickMoveStack(shiftPlayer, 3).isEmpty(),
                              "Removed recipe's old preview was shift-clicked");
            for (MismatchedWorkbenchMenu live : List.of(pending, shift)) {
                helper.assertTrue(!live.isCommitted() && live.getSlot(0).getItem().is(Items.IRON_BOOTS)
                                          && live.getSlot(1).getItem().is(Items.GOLDEN_BOOTS),
                                  "Revoked preview consumed inputs or committed the transaction");
                helper.assertTrue(live.getSlot(2).getItem().isEmpty() && live.getSlot(3).getItem().isEmpty(),
                                  "Recipe removal did not revoke both live previews");
            }
            helper.assertTrue(ItemStack.matches(frozen, committed.getSlot(3).safeTake(1, 1, committedPlayer)),
                              "Recipe removal revoked or changed the already committed second output");

            helper.assertTrue(server.getRecipeManager().byKey(COMPOSITION_RECIPE).isEmpty(),
                              "Disabled workbench composition recipe remained loaded");
            helper.assertTrue(server.getRecipeManager().byKey(REPAIR_RECIPE).isEmpty(),
                              "Disabled crafting repair recipe remained loaded");
            helper.assertTrue(server.getRecipeManager().byKey(BLOCK_RECIPE).isPresent(),
                              "Removing data-driven transformations also removed the block acquisition recipe");
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(WORKBENCH_POS, ModContent.mismatchedWorkbenchBlock().orElseThrow());
            MismatchedWorkbenchMenu menu = new MismatchedWorkbenchMenu(
                    1, player.getInventory(), ContainerLevelAccess.create(
                    helper.getLevel(), helper.absolutePos(WORKBENCH_POS)));
            menu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
            menu.getSlot(1).set(new ItemStack(Items.GOLDEN_BOOTS));
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                              "Removed composition recipe still created previews");
            helper.assertTrue(acquisitionRecipe(helper).matches(workbenchInput(
                                      Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS,
                                      Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS), helper.getLevel()),
                              "Block acquisition recipe stopped matching after composition removal");
            CraftingInput disabledRepair = CraftingInput.of(2, 1, List.of(
                    GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS),
                    new ItemStack(Items.IRON_BOOTS)));
            helper.assertTrue(helper.getLevel().recipeAccess()
                                    .getRecipeFor(RecipeType.CRAFTING, disabledRepair, helper.getLevel()).isEmpty(),
                              "Removed crafting repair recipe still matched a source donor");
            CraftingInput mixedPair = CraftingInput.of(2, 1, List.of(
                    GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).primary(),
                    GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse()));
            helper.assertTrue(helper.getLevel().recipeAccess()
                                    .getRecipeFor(RecipeType.CRAFTING, mixedPair, helper.getLevel()).isEmpty(),
                              "Removing crafting repair restored vanilla's bare Mixed/Mixed result");
            CraftingInput ordinaryRepair = CraftingInput.of(2, 1, List.of(
                    new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_BOOTS)));
            var ordinaryRecipe = helper.getLevel().recipeAccess()
                                       .getRecipeFor(RecipeType.CRAFTING, ordinaryRepair, helper.getLevel())
                                       .orElseThrow(() -> new AssertionError("Removing mixed repair removed vanilla repair"));
            helper.assertTrue(ordinaryRecipe.value() instanceof RepairItemRecipe,
                              "Ordinary repair selected the wrong recipe after data-pack removal: " + ordinaryRecipe);
            menu.removed(player);
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare the workbench recipe-removal data pack", exception);
        } finally {
            pending.removed(pendingPlayer);
            shift.removed(shiftPlayer);
            committed.removed(committedPlayer);
            server.reloadResources(originalPacks).join();
        }
        helper.assertTrue(server.getRecipeManager().byKey(COMPOSITION_RECIPE).isPresent(),
                          "Normal workbench composition recipe was not restored");
        helper.assertTrue(server.getRecipeManager().byKey(REPAIR_RECIPE).isPresent(),
                          "Normal crafting repair recipe was not restored");
        assertReplacementReload(helper, server, originalPacks, pack);
        helper.succeed();
    }

    private static void assertReplacementReload(
            GameTestHelper helper, MinecraftServer server, List<String> originalPacks, Path pack) {
        ServerPlayer normalPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer shiftPlayer = helper.makeMockServerPlayerInLevel();
        MismatchedWorkbenchMenu normal = readyMenu(helper, normalPlayer, 21);
        MismatchedWorkbenchMenu shift = readyMenu(helper, shiftPlayer, 22);
        try {
            Files.writeString(pack.resolve("data/mismatchedboots/recipe/mixed_boots_workbench.json"),
                              "{\"type\":\"mismatchedboots-test:reversed_workbench\"}", StandardCharsets.UTF_8);
            var selected = new ArrayList<>(originalPacks);
            selected.add(DISABLING_PACK_ID);
            server.reloadResources(selected).join();
            normal.clicked(2, 0, ContainerInput.PICKUP, normalPlayer);
            helper.assertTrue(normal.getCarried().isEmpty(), "Stale normal click extracted a replaced preview");
            helper.assertTrue(shift.quickMoveStack(shiftPlayer, 2).isEmpty(),
                              "Stale shift-click extracted a replaced preview");
            for (MismatchedWorkbenchMenu live : List.of(normal, shift)) {
                helper.assertTrue(!live.isCommitted() && live.getSlot(0).getItem().is(Items.IRON_BOOTS)
                                          && live.getSlot(1).getItem().is(Items.GOLDEN_BOOTS),
                                  "Replacing previews consumed inputs");
                GameTestStacks.assertOrientation(helper, live.getSlot(2).getItem(),
                                                 Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "replacement primary preview");
                GameTestStacks.assertOrientation(helper, live.getSlot(3).getItem(),
                                                 Items.IRON_BOOTS, Items.GOLDEN_BOOTS, "replacement inverse preview");
            }
            normal.clicked(2, 0, ContainerInput.PICKUP, normalPlayer);
            GameTestStacks.assertOrientation(helper, normal.getCarried(),
                                             Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "new normal extraction");
            GameTestStacks.assertOrientation(helper, shift.quickMoveStack(shiftPlayer, 2),
                                             Items.GOLDEN_BOOTS, Items.IRON_BOOTS, "new shift extraction");
            helper.assertTrue(normal.isCommitted() && shift.isCommitted(), "Fresh extraction did not commit");
        } catch (IOException exception) {
            throw new AssertionError("Could not write replacement workbench recipe", exception);
        } finally {
            normal.removed(normalPlayer);
            shift.removed(shiftPlayer);
            server.reloadResources(originalPacks).join();
        }
    }

    private static MismatchedWorkbenchMenu readyMenu(GameTestHelper helper, ServerPlayer player, int id) {
        var menu = new MismatchedWorkbenchMenu(id, player.getInventory(),
                                               ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(WORKBENCH_POS)));
        menu.getSlot(0).set(new ItemStack(Items.IRON_BOOTS));
        menu.getSlot(1).set(new ItemStack(Items.GOLDEN_BOOTS));
        helper.assertTrue(!menu.getSlot(2).getItem().isEmpty() && !menu.getSlot(3).getItem().isEmpty(),
                          "Could not prepare live previews before reload");
        return menu;
    }

    private static CraftingRecipe acquisitionRecipe(GameTestHelper helper) {
        return (CraftingRecipe) helper.getLevel().getServer().getRecipeManager()
                                      .byKey(BLOCK_RECIPE)
                                      .orElseThrow(() -> new AssertionError("Workbench acquisition recipe is not loaded"))
                                      .value();
    }

    private static CraftingInput workbenchInput(
            net.minecraft.world.item.Item first,
            net.minecraft.world.item.Item second,
            net.minecraft.world.item.Item third,
            net.minecraft.world.item.Item fourth,
            net.minecraft.world.item.Item fifth,
            net.minecraft.world.item.Item sixth) {
        return CraftingInput.of(3, 3, workbenchStacks(first, second, third, fourth, fifth, sixth));
    }

    private static List<ItemStack> workbenchStacks(
            net.minecraft.world.item.Item first,
            net.minecraft.world.item.Item second,
            net.minecraft.world.item.Item third,
            net.minecraft.world.item.Item fourth,
            net.minecraft.world.item.Item fifth,
            net.minecraft.world.item.Item sixth) {
        return List.of(
                new ItemStack(first), new ItemStack(Items.SHEARS), new ItemStack(second),
                new ItemStack(third), new ItemStack(Items.DIAMOND), new ItemStack(fourth),
                new ItemStack(fifth), ItemStack.EMPTY, new ItemStack(sixth));
    }

    private static List<ItemStack> copyStacks(CraftingInput input) {
        List<ItemStack> stacks = new ArrayList<>(input.size());
        for (int index = 0; index < input.size(); index++) {
            stacks.add(input.getItem(index).copy());
        }
        return stacks;
    }

    private static void writeDisablingPack(Path pack) throws IOException {
        PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                                  {"pack":{"description":"Disable MismatchedBoots data recipes",
                                  "min_format":[%d,%d],"max_format":[%d,%d]}}
                                  """.formatted(version.major(), version.minor(), version.major(), version.minor()),
                          StandardCharsets.UTF_8);
        Path recipe = pack.resolve("data/mismatchedboots/recipe/mixed_boots_workbench.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, """
                {"fabric:load_conditions":[{"condition":"fabric:false"}],
                "type":"mismatchedboots:mixed_boots_workbench"}
                """, StandardCharsets.UTF_8);
        Path repair = pack.resolve("data/mismatchedboots/recipe/mixed_boots_repair.json");
        Files.writeString(repair, """
                {"fabric:load_conditions":[{"condition":"fabric:false"}],
                "type":"mismatchedboots:mixed_boots_repair"}
                """, StandardCharsets.UTF_8);
    }
}
