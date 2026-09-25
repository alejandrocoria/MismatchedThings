package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SmithingGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void mixedBootsRouteToBaseSlotIncludingShiftClick(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        SmithingMenu menu = menu(helper, player);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).mayPlace(mixed),
                "Mixed boots are not accepted in the smithing base slot");
        helper.assertTrue(!menu.getSlot(SmithingMenu.TEMPLATE_SLOT).mayPlace(mixed),
                "Mixed boots are accepted as a smithing template");
        helper.assertTrue(!menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).mayPlace(mixed),
                "Mixed boots are accepted as a smithing addition");
        menu.getSlot(4).set(mixed);
        menu.quickMoveStack(player, 4);
        helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().is(ModContent.MIXED_BOOTS_KEY),
                "Shift-click did not route mixed boots to the smithing base slot");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void trimBothSourcesPreservesRootAndConsumesInputsOnce(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData before = GameTestStacks.data(mixed);
        mixed.setDamageValue(9);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Anvil name"));
        mixed.set(DataComponents.REPAIR_COST, 11);
        mixed.set(TestContent.requireOpaqueComponent(), "root-only");
        var unbreaking = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(unbreaking, 2);
        mixed.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        ItemStack original = mixed.copy();
        SmithingMenu menu = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 2), mixed,
                new ItemStack(Items.DIAMOND, 2));

        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        assertTrims(helper, preview, true, true);
        helper.assertValueEqual(preview.getDamageValue(), 9, "preview raw damage");
        helper.assertValueEqual(preview.get(DataComponents.REPAIR_COST), 11, "preview prior work");
        helper.assertValueEqual(preview.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(unbreaking), 2, "live root enchantment");
        helper.assertValueEqual(preview.get(TestContent.requireOpaqueComponent()), "root-only", "root-only component");
        helper.assertTrue(preview.getHoverName().equals(Component.literal("Anvil name")),
                "Smithing lost the live root name");
        helper.assertTrue(!preview.has(DataComponents.TRIM), "Source trim leaked onto the root");
        helper.assertTrue(ItemStack.matches(original, mixed), "Preview mutated the input root");
        helper.assertTrue(!before.left().source().has(DataComponents.TRIM)
                        && !before.right().source().has(DataComponents.TRIM),
                "Preview mutated the original source snapshots");

        ResultContainer resultSlots = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        helper.assertTrue(resultSlots.getRecipeUsed() != null, "Smithing preview has no primary recipe");
        // Mock players have no packet connection; skip only the recipe-book award on extraction.
        resultSlots.setRecipeUsed(null);
        ItemStack taken = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT);
        assertTrims(helper, taken, true, true);
        helper.assertValueEqual(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().getCount(), 1,
                "Smithing template consumption");
        helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(),
                "Smithing did not consume its base");
        helper.assertValueEqual(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount(), 1,
                "Smithing addition consumption");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void trimOnlyEligibleLeftSource(GameTestHelper helper) {
        assertSingleEligibleSource(helper, Items.IRON_BOOTS, TestContent.requireComponentBoots(), true);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void trimOnlyEligibleRightSource(GameTestHelper helper) {
        assertSingleEligibleSource(helper, TestContent.requireComponentBoots(), Items.IRON_BOOTS, false);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void repeatedTrimProducesNoResult(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack iron = ordinaryTrim(helper, player, Items.IRON_BOOTS);
        ItemStack gold = ordinaryTrim(helper, player, Items.GOLDEN_BOOTS);
        ItemStack mixed = GameTestStacks.pair(iron, gold).primary();
        SmithingMenu menu = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), mixed, new ItemStack(Items.DIAMOND));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Repeating the same trim on both sources produced an output");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void foreignSmithingResultTakesPrecedence(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        SmithingMenu menu = smithing(helper, player,
                new ItemStack(Items.SHEARS), GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS),
                new ItemStack(Items.FLINT));
        helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().is(Items.IRON_BOOTS),
                "A loaded recipe for the mixed base lost to the source-wise fallback");
        helper.succeed();
    }

    private static void assertSingleEligibleSource(GameTestHelper helper, Item left, Item right, boolean leftChanges) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        SmithingMenu menu = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                GameTestStacks.mixed(left, right), new ItemStack(Items.DIAMOND));
        assertTrims(helper, menu.getSlot(SmithingMenu.RESULT_SLOT).getItem(), leftChanges, !leftChanges);
        helper.succeed();
    }

    private static ItemStack ordinaryTrim(
            GameTestHelper helper, net.minecraft.world.entity.player.Player player, Item item) {
        SmithingMenu menu = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                new ItemStack(item), new ItemStack(Items.DIAMOND));
        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        helper.assertTrue(result.has(DataComponents.TRIM), "Ordinary smithing did not apply the trim");
        return result;
    }

    private static void assertTrims(
            GameTestHelper helper, ItemStack stack, boolean left, boolean right) {
        helper.assertTrue(!stack.isEmpty(), "Mixed smithing produced no result");
        MixedBootsData data = GameTestStacks.data(stack);
        helper.assertValueEqual(data.left().source().has(DataComponents.TRIM), left, "left source trim");
        helper.assertValueEqual(data.right().source().has(DataComponents.TRIM), right, "right source trim");
    }

    private static SmithingMenu smithing(
            GameTestHelper helper, net.minecraft.world.entity.player.Player player,
            ItemStack template, ItemStack base, ItemStack addition) {
        SmithingMenu menu = menu(helper, player);
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        return menu;
    }

    private static SmithingMenu menu(GameTestHelper helper, net.minecraft.world.entity.player.Player player) {
        return new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
    }
}
