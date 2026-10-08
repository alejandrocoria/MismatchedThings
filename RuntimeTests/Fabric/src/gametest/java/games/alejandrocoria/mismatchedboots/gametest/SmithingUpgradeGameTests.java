package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SmithingUpgradeGameTests {
    private static final ResourceKey<Recipe<?>> COMPONENT_RECIPE = recipe("component_boots_upgrade");
    private static final ResourceKey<Recipe<?>> IRON_RECIPE = recipe("iron_boots_upgrade");
    private static final ResourceKey<Recipe<?>> TEMPLATE_FREE_COMPONENT_RECIPE = recipe("template_free_component_upgrade");
    private static final ResourceKey<Recipe<?>> TEMPLATE_FREE_IRON_RECIPE = recipe("template_free_iron_upgrade");

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void templateFreeRecipesChangeOnlyEligibleSourcesInBothOrientations(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        for (Item other : new Item[]{Items.IRON_BOOTS, Items.GOLDEN_BOOTS}) {
            var pair = GameTestStacks.pair(new ItemStack(TestContent.COMPONENT_BOOTS), new ItemStack(other));
            for (ItemStack mixed : new ItemStack[]{pair.primary(), pair.inverse()}) {
                mixed.setDamageValue(43);
                mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Live root"));
                ItemStack before = mixed.copy();
                ItemStack output = smithing(helper, player, ItemStack.EMPTY, mixed,
                        new ItemStack(Items.AMETHYST_SHARD)).getSlot(SmithingMenu.RESULT_SLOT).getItem();
                helper.assertTrue(!output.isEmpty(), "Template-free recipe produced no mixed result");
                MixedBootsData original = GameTestStacks.data(before);
                MixedBootsData changed = GameTestStacks.data(output);
                assertTemplateFreeSource(helper, original.left().source(), changed.left().source());
                assertTemplateFreeSource(helper, original.right().source(), changed.right().source());
                helper.assertValueEqual(output.getDamageValue(), 43, "preserved live root wear");
                helper.assertValueEqual(output.getHoverName(), mixed.getHoverName(), "preserved root name");
                helper.assertTrue(ItemStack.matches(before, mixed), "Template-free preview mutated its base");

                SmithingMenu repeated = smithing(helper, player, ItemStack.EMPTY, output.copy(),
                        new ItemStack(Items.AMETHYST_SHARD));
                helper.assertTrue(repeated.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                        "Already transformed sources produced another template-free result");
            }
        }
        // An already-upgraded side must not prevent a different eligible side changing.
        ItemStack installed = new ItemStack(TestContent.COMPONENT_BOOTS);
        installed.set(TestContent.ADDED_COMPONENT, "installed without template");
        ItemStack output = smithing(helper, player, ItemStack.EMPTY,
                GameTestStacks.pair(installed, new ItemStack(Items.IRON_BOOTS)).primary(),
                new ItemStack(Items.AMETHYST_SHARD)).getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertValueEqual(GameTestStacks.data(output).right().source().get(TestContent.ADDED_COMPONENT),
                "installed without template", "remaining eligible side");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void templateFreeMenuInsertionAndBothTakeModesConsumeOnce(GameTestHelper helper) {
        for (boolean shiftClick : new boolean[]{false, true}) {
            ServerPlayer player = connectedMockPlayer(helper);
            SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
            ItemStack mixed = GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS);
            ItemStack addition = new ItemStack(Items.AMETHYST_SHARD, 2);
            helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).mayPlace(mixed), "Base slot rejected mixed boots");
            helper.assertTrue(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).mayPlace(addition), "Addition slot rejected upgrade");
            if (shiftClick) {
                menu.getSlot(4).set(mixed);
                menu.getSlot(5).set(addition);
                menu.quickMoveStack(player, 4);
                menu.quickMoveStack(player, 5);
            } else {
                menu.setCarried(mixed);
                menu.clicked(SmithingMenu.BASE_SLOT, 0, ContainerInput.PICKUP, player);
                menu.setCarried(addition);
                menu.clicked(SmithingMenu.ADDITIONAL_SLOT, 0, ContainerInput.PICKUP, player);
            }
            helper.assertTrue(!menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Insertion produced no preview");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_COMPONENT_RECIPE), 0,
                    "preview primary attribution");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_IRON_RECIPE), 0,
                    "preview secondary attribution");
            ItemStack taken;
            if (shiftClick) {
                taken = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
            } else {
                menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
                taken = menu.getCarried();
            }
            helper.assertTrue(taken.is(ModContent.MIXED_BOOTS_KEY), "Template-free result was not extracted");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_COMPONENT_RECIPE), 1,
                    "primary attribution on take");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_IRON_RECIPE), 1,
                    "secondary attribution on take");
            helper.assertTrue(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty(), "Template slot acquired an item");
            helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(), "Base was not consumed");
            helper.assertValueEqual(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount(), 1,
                    "single addition payment for two changed sources");
            helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Taken preview remained available");
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void templateFreeRejectionsAndBlockedTakePreserveInputs(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        ItemStack mixed = GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS);
        for (ItemStack template : new ItemStack[]{new ItemStack(Items.PAPER), new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)}) {
            SmithingMenu rejected = smithing(helper, player, template, mixed.copy(), new ItemStack(Items.AMETHYST_SHARD));
            helper.assertTrue(rejected.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Populated wrong template was ignored");
            helper.assertTrue(ItemStack.matches(rejected.getSlot(SmithingMenu.BASE_SLOT).getItem(), mixed), "Rejection changed its base");
        }
        for (ItemStack addition : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.DIRT)}) {
            helper.assertTrue(smithing(helper, player, ItemStack.EMPTY, mixed.copy(), addition)
                    .getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Missing/wrong addition produced an upgrade");
        }
        helper.assertTrue(smithing(helper, player, ItemStack.EMPTY,
                GameTestStacks.mixed(Items.GOLDEN_BOOTS, Items.LEATHER_BOOTS), new ItemStack(Items.AMETHYST_SHARD))
                .getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Ineligible sources upgraded");
        helper.assertTrue(smithing(helper, player, ItemStack.EMPTY,
                GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.IRON_BOOTS), new ItemStack(Items.NETHERITE_INGOT))
                .getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Required vanilla template became optional");

        SmithingMenu menu = smithing(helper, player, ItemStack.EMPTY, mixed.copy(), new ItemStack(Items.AMETHYST_SHARD, 2));
        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        helper.assertTrue(!preview.isEmpty(), "Blocked-take fixture has no preview");
        for (int slot = SmithingMenu.RESULT_SLOT + 1; slot < menu.slots.size(); slot++) {
            menu.getSlot(slot).set(new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT).isEmpty(), "Full inventory accepted output");
        helper.assertTrue(ItemStack.matches(preview, menu.getSlot(SmithingMenu.RESULT_SLOT).getItem()), "Blocked take lost preview");
        helper.assertTrue(ItemStack.matches(mixed, menu.getSlot(SmithingMenu.BASE_SLOT).getItem()), "Blocked take consumed base");
        helper.assertValueEqual(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount(), 2, "blocked payment");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_COMPONENT_RECIPE), 0, "blocked primary attribution");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_IRON_RECIPE), 0, "blocked secondary attribution");
        menu.removed(player);
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_COMPONENT_RECIPE), 0, "closed primary attribution");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, TEMPLATE_FREE_IRON_RECIPE), 0, "closed secondary attribution");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void templateFreeOrdinaryAndForeignMixedRecipesKeepTheirResults(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack nativeOutput = smithing(helper, player, ItemStack.EMPTY, new ItemStack(Items.IRON_BOOTS),
                new ItemStack(Items.AMETHYST_SHARD)).getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(nativeOutput.is(Items.IRON_BOOTS), "Non-mixed recipe result changed item");
        helper.assertValueEqual(nativeOutput.get(TestContent.ADDED_COMPONENT), "installed without template", "ordinary recipe installation");
        ItemStack foreignOutput = smithing(helper, player, ItemStack.EMPTY,
                GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS), new ItemStack(Items.COAL))
                .getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(foreignOutput.is(Items.IRON_BOOTS), "Loaded mixed-base recipe lost its native result");
        helper.assertValueEqual(foreignOutput.get(TestContent.ADDED_COMPONENT), "foreign template-free result",
                "native mixed-base recipe component");
        helper.succeed();
    }

    private static void assertTemplateFreeSource(GameTestHelper helper, ItemStack original, ItemStack changed) {
        if (original.is(Items.GOLDEN_BOOTS)) {
            helper.assertTrue(ItemStack.matches(original, changed), "Ineligible source changed");
        } else {
            ItemStack expected = original.copy();
            expected.set(TestContent.ADDED_COMPONENT, "installed without template");
            helper.assertTrue(ItemStack.matches(expected, changed), "Eligible source did not match its loaded recipe");
        }
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteUpgradeChangesBothSourcesAndPreservesLiveRoot(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack trimmedDiamond = ordinaryTrim(helper, player, Items.DIAMOND_BOOTS);
        ItemStack mixed = GameTestStacks.pair(trimmedDiamond, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        mixed.setDamageValue(19);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Book and anvil"));
        mixed.set(DataComponents.REPAIR_COST, 15);
        var unbreaking = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(unbreaking, 3);
        mixed.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        ItemStack before = mixed.copy();

        SmithingMenu menu = netherite(helper, player, mixed);
        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        MixedBootsData sources = GameTestStacks.data(result);
        helper.assertTrue(sources.left().source().is(Items.NETHERITE_BOOTS), "Left diamond source did not upgrade");
        helper.assertTrue(sources.right().source().is(Items.NETHERITE_BOOTS), "Right diamond source did not upgrade");
        helper.assertTrue(sources.left().source().has(DataComponents.TRIM), "Existing source trim was lost");
        helper.assertTrue(!result.has(DataComponents.TRIM), "Source trim leaked onto the root");
        helper.assertTrue(!result.canBeHurtBy(helper.getLevel().damageSources().inFire()),
                "Two upgraded netherite sources did not project shared fire resistance");
        helper.assertValueEqual(result.getMaxDamage(), new ItemStack(Items.NETHERITE_BOOTS).getMaxDamage(),
                "upgraded maximum durability");
        helper.assertValueEqual(result.getDamageValue(), 19, "preserved raw root damage");
        helper.assertValueEqual(result.get(DataComponents.REPAIR_COST), 15, "prior-work cost");
        helper.assertTrue(result.getHoverName().equals(Component.literal("Book and anvil")),
                "Custom root name was lost");
        helper.assertValueEqual(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(unbreaking), 3, "book enchantment");
        helper.assertValueEqual(result.get(DataComponents.ATTRIBUTE_MODIFIERS),
                MixedBootsFactory.createPair(sources.left().source(), sources.right().source())
                        .primary().get(DataComponents.ATTRIBUTE_MODIFIERS), "upgraded attributes");
        helper.assertValueEqual(result.get(DataComponents.EQUIPPABLE).assetId(),
                sources.left().source().get(DataComponents.EQUIPPABLE).assetId(), "upgraded equipment asset");
        helper.assertTrue(result.isValidRepairItem(new ItemStack(Items.NETHERITE_INGOT)),
                "Upgraded repair material was not recomputed");
        helper.assertTrue(ItemStack.matches(before, mixed), "Upgrade preview mutated the root input");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteUpgradeChangesOnlyLeftSource(GameTestHelper helper) {
        assertSingleNetheriteUpgrade(helper, Items.DIAMOND_BOOTS, Items.IRON_BOOTS, true);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteUpgradeChangesOnlyRightSource(GameTestHelper helper) {
        assertSingleNetheriteUpgrade(helper, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, false);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteUpgradeRejectsIneligibleSources(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        SmithingMenu menu = netherite(helper, player, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Ineligible sources produced a netherite upgrade");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void upgradeClampsRawRootDamageToNewMaximum(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.IRON_BOOTS);
        mixed.set(DataComponents.MAX_DAMAGE, 2000);
        mixed.setDamageValue(1000);
        ItemStack result = netherite(helper, player, mixed).getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!result.isEmpty(), "Upgrade with high root damage produced no result");
        helper.assertValueEqual(result.getDamageValue(), result.getMaxDamage(), "clamped root damage");
        helper.assertValueEqual(mixed.getDamageValue(), 1000, "original root damage");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void loadedModdedRecipesTransformItemsAndUnknownComponents(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack custom = new ItemStack(TestContent.COMPONENT_BOOTS);
        custom.set(TestContent.OPAQUE_COMPONENT, "original");
        custom.set(TestContent.REMOVED_COMPONENT, "remove me");
        custom.setDamageValue(21);
        ItemStack mixed = GameTestStacks.pair(custom, new ItemStack(Items.IRON_BOOTS)).primary();
        mixed.setDamageValue(43);
        ItemStack before = mixed.copy();

        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER), mixed,
                new ItemStack(Items.QUARTZ));
        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        MixedBootsData sources = GameTestStacks.data(result);
        helper.assertTrue(sources.left().source().is(TestContent.UPGRADED_COMPONENT_BOOTS),
                "Loaded custom recipe did not change the left item");
        helper.assertTrue(sources.right().source().is(Items.DIAMOND_BOOTS),
                "Distinct loaded recipe did not change the right item");
        helper.assertValueEqual(sources.left().source().get(TestContent.OPAQUE_COMPONENT),
                "changed by recipe", "changed source component");
        helper.assertValueEqual(sources.left().source().get(TestContent.ADDED_COMPONENT),
                "added by recipe", "added source component");
        helper.assertTrue(!sources.left().source().has(TestContent.REMOVED_COMPONENT),
                "Removed source component survived");
        helper.assertValueEqual(result.get(TestContent.OPAQUE_COMPONENT), "changed by recipe",
                "changed owner component projection");
        helper.assertValueEqual(result.get(TestContent.ADDED_COMPONENT), "added by recipe",
                "added owner component projection");
        helper.assertTrue(!result.has(TestContent.REMOVED_COMPONENT),
                "Removed owner component survived");
        helper.assertValueEqual(result.getMaxDamage(),
                (500 + new ItemStack(Items.DIAMOND_BOOTS).getMaxDamage()) / 2, "new source maxima");
        helper.assertValueEqual(result.getDamageValue(), 43, "live raw root damage");
        helper.assertTrue(result.isValidRepairItem(new ItemStack(Items.DIAMOND)),
                "Modded repair material was not projected");
        helper.assertTrue(ItemStack.matches(before, mixed), "Custom upgrade preview mutated its input");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void invalidAssembledItemCannotBecomeABootHalf(GameTestHelper helper) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack before = mixed.copy();
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.BRICK), mixed,
                new ItemStack(Items.FLINT));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Non-feet assembled item became a mixed boot source");
        helper.assertTrue(ItemStack.matches(before, mixed), "Rejected upgrade mutated its input");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void distinctRecipesAwardOnlyAfterTakingOneOutput(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        ItemStack mixed = GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS);
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER, 2), mixed,
                new ItemStack(Items.QUARTZ, 2));
        ResultContainer resultSlots = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        helper.assertValueEqual(resultSlots.getRecipeUsed().id(), COMPONENT_RECIPE, "primary recipe holder");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 0, "preview primary trigger");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 0, "preview secondary trigger");
        helper.assertTrue(!player.getRecipeBook().contains(COMPONENT_RECIPE)
                        && !player.getRecipeBook().contains(IRON_RECIPE),
                "Preview awarded recipes before extraction");

        ItemStack taken = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
        helper.assertTrue(taken.is(ModContent.MIXED_BOOTS_KEY), "Distinct-recipe output was not extracted");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 1, "primary crafted trigger");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 1, "secondary crafted trigger");
        helper.assertTrue(player.getRecipeBook().contains(COMPONENT_RECIPE)
                        && player.getRecipeBook().contains(IRON_RECIPE),
                "Both loaded recipes were not awarded to the recipe book");
        helper.assertValueEqual(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().getCount(), 1,
                "template consumed more than once");
        helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(),
                "Mixed base was not consumed");
        helper.assertValueEqual(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount(), 1,
                "addition consumed more than once");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 1,
                "Secondary recipe fired twice for one output");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void inPlaceInputEditsDoNotAwardTheStaleSecondaryRecipe(GameTestHelper helper) {
        for (boolean changeCount : new boolean[]{true, false}) {
            ServerPlayer player = connectedMockPlayer(helper);
            SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER, 2),
                    GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS),
                    new ItemStack(Items.QUARTZ, 2));
            // Emulate another consumer editing the live stack without a slot-change
            // notification: the cached secondary attribution must match full inputs.
            ItemStack template = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
            if (changeCount) {
                template.setCount(3);
            } else {
                template.set(DataComponents.CUSTOM_NAME, Component.literal("Changed after preview"));
            }
            helper.assertTrue(!menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT).isEmpty(),
                    "Input-edit fixture did not extract its existing preview");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 1,
                    "Vanilla primary recipe attribution was suppressed");
            helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 0,
                    "Stale secondary attribution ignored an input count/component edit");
            helper.assertTrue(!player.getRecipeBook().contains(IRON_RECIPE),
                    "Stale secondary recipe was added to the recipe book");
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void ordinaryClickAwardsBothDistinctRecipes(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER),
                GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS),
                new ItemStack(Items.QUARTZ));
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ModContent.MIXED_BOOTS_KEY),
                "Ordinary click did not take the mixed result");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 1,
                "Ordinary click primary crafted trigger");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 1,
                "Ordinary click secondary crafted trigger");
        helper.assertTrue(player.getRecipeBook().contains(COMPONENT_RECIPE)
                        && player.getRecipeBook().contains(IRON_RECIPE),
                "Ordinary click did not award both recipes");
        helper.assertTrue(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                        && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                        && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Ordinary click did not consume each smithing input");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void blockedShiftClickAwardsNeitherDistinctRecipe(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER),
                GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS),
                new ItemStack(Items.QUARTZ));
        for (int slot = SmithingMenu.RESULT_SLOT + 1; slot < menu.slots.size(); slot++) {
            menu.getSlot(slot).set(new ItemStack(Items.STONE, 64));
        }

        ItemStack moved = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
        helper.assertTrue(moved.isEmpty(), "Full inventory accepted a smithing shift-click");
        helper.assertTrue(!menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Blocked shift-click removed the preview");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 0,
                "Blocked shift-click triggered the primary recipe");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 0,
                "Blocked shift-click triggered the secondary recipe");
        helper.assertTrue(!player.getRecipeBook().contains(COMPONENT_RECIPE)
                        && !player.getRecipeBook().contains(IRON_RECIPE),
                "Blocked shift-click awarded a recipe");
        helper.assertTrue(!menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                        && !menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                        && !menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Blocked shift-click consumed a smithing input");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void replacedPreviewDoesNotAwardStaleSecondaryRecipe(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER),
                GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS),
                new ItemStack(Items.QUARTZ));
        helper.assertTrue(!menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Expected initial two-recipe preview");
        menu.getSlot(SmithingMenu.BASE_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(GameTestStacks.data(preview).left().source().is(Items.DIAMOND_BOOTS),
                "Replacement preview did not use the iron recipe");
        menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 0,
                "Stale custom recipe was triggered");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 1,
                "Current iron recipe was not triggered once");
        helper.assertTrue(!player.getRecipeBook().contains(COMPONENT_RECIPE),
                "Stale custom recipe was awarded to the recipe book");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void closingPreviewAwardsNoRecipe(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        SmithingMenu menu = smithing(helper, player, new ItemStack(Items.PAPER),
                GameTestStacks.mixed(TestContent.COMPONENT_BOOTS, Items.IRON_BOOTS),
                new ItemStack(Items.QUARTZ));
        helper.assertTrue(!menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Expected preview before closing");
        menu.removed(player);
        helper.assertValueEqual(RecipeCraftedProbe.count(player, COMPONENT_RECIPE), 0,
                "Closing triggered the primary recipe");
        helper.assertValueEqual(RecipeCraftedProbe.count(player, IRON_RECIPE), 0,
                "Closing triggered the secondary recipe");
        helper.succeed();
    }

    private static void assertSingleNetheriteUpgrade(
            GameTestHelper helper, Item left, Item right, boolean leftChanges) {
        var player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(left, right);
        ItemStack result = netherite(helper, player, mixed).getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!result.isEmpty(), "Single-source netherite upgrade was rejected");
        helper.assertTrue(!result.has(DataComponents.DAMAGE_RESISTANT)
                        && result.canBeHurtBy(helper.getLevel().damageSources().inFire()),
                "Single-source netherite upgrade granted whole-item fire resistance");
        MixedBootsData sources = GameTestStacks.data(result);
        helper.assertTrue(sources.left().source().is(leftChanges ? Items.NETHERITE_BOOTS : left),
                "Wrong left source after upgrade");
        helper.assertTrue(sources.right().source().is(leftChanges ? right : Items.NETHERITE_BOOTS),
                "Wrong right source after upgrade");
        helper.assertValueEqual(result.getMaxDamage(),
                (new ItemStack(leftChanges ? Items.NETHERITE_BOOTS : left).getMaxDamage()
                        + new ItemStack(leftChanges ? right : Items.NETHERITE_BOOTS).getMaxDamage()) / 2,
                "single-source upgraded maximum");
        helper.succeed();
    }

    private static ItemStack ordinaryTrim(
            GameTestHelper helper, Player player, Item item) {
        return smithing(helper, player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                new ItemStack(item), new ItemStack(Items.DIAMOND))
                .getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
    }

    private static SmithingMenu netherite(GameTestHelper helper, Player player, ItemStack base) {
        return smithing(helper, player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base,
                new ItemStack(Items.NETHERITE_INGOT));
    }

    private static SmithingMenu smithing(
            GameTestHelper helper, Player player, ItemStack template, ItemStack base, ItemStack addition) {
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        return menu;
    }

    private static ServerPlayer connectedMockPlayer(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.makeMockServerPlayer(helper, net.minecraft.world.level.GameType.CREATIVE);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    private static ResourceKey<Recipe<?>> recipe(String path) {
        return ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("mismatchedboots-test", path));
    }
}
