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

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void netheriteUpgradeChangesBothSourcesAndPreservesLiveRoot(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
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
        helper.assertTrue(result.get(DataComponents.TRIM).equals(sources.left().source().get(DataComponents.TRIM)),
                "Left trim was not projected to the root");
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
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        SmithingMenu menu = netherite(helper, player, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Ineligible sources produced a netherite upgrade");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void upgradeClampsRawRootDamageToNewMaximum(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
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
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
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
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
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
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(left, right);
        ItemStack result = netherite(helper, player, mixed).getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!result.isEmpty(), "Single-source netherite upgrade was rejected");
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
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
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
