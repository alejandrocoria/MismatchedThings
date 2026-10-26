package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

@SuppressWarnings("removal")
public final class CraftingRepairFlowGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void playerGridNormalTakeAppliesTheCompleteRepairContract(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.giveExperienceLevels(5);
        int experienceBefore = player.experienceLevel;
        Holder<Enchantment> ordinary = enchantment(helper, Enchantments.UNBREAKING);
        Holder<Enchantment> curse = enchantment(helper, Enchantments.VANISHING_CURSE);
        CustomData opaque = customData("preserved");
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        int rootMaximum = mixed.getMaxDamage();
        mixed.setDamageValue(rootMaximum - 20);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Repair target"));
        mixed.set(DataComponents.REPAIR_COST, 17);
        mixed.set(DataComponents.CUSTOM_DATA, opaque);
        setEnchantments(mixed, ordinary, 3, curse, 1);
        MixedBootsData sources = GameTestStacks.data(mixed);
        ItemStack donor = new ItemStack(Items.IRON_BOOTS);
        donor.setDamageValue(donor.getMaxDamage() - 10);
        setEnchantments(donor, ordinary, 1, curse, 2);
        int expectedDamage = expectedDamage(mixed, donor);

        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(mixed);
        menu.getInputGridSlots().get(3).set(donor);

        ItemStack preview = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem();
        assertRichResult(helper, preview, sources, rootMaximum, expectedDamage, ordinary, curse, opaque,
                         "2x2 preview");
        ItemStack taken = menu.getSlot(InventoryMenu.RESULT_SLOT).safeTake(1, 1, player);
        assertRichResult(helper, taken, sources, rootMaximum, expectedDamage, ordinary, curse, opaque,
                         "2x2 extracted result");
        assertConsumedOnce(helper, menu, 0, 3, "2x2 normal take");
        helper.assertValueEqual(player.experienceLevel, experienceBefore, "crafting repair experience level");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void playerGridShiftClickAcceptsTheRightSourceInReverseOrder(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack donor = new ItemStack(Items.GOLDEN_BOOTS);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData sources = GameTestStacks.data(mixed);
        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(donor);
        menu.getInputGridSlots().get(2).set(mixed);

        helper.assertTrue(!menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty(),
                          "2x2 reverse-order repair had no preview");
        ItemStack moved = menu.quickMoveStack(player, InventoryMenu.RESULT_SLOT);

        helper.assertTrue(!moved.isEmpty(), "2x2 reverse-order repair could not be shift-clicked");
        helper.assertValueEqual(moved.getDamageValue(), 0, "saturated 2x2 repair damage");
        helper.assertTrue(GameTestStacks.data(moved).equals(sources),
                          "2x2 shift-click changed source snapshots");
        assertConsumedOnce(helper, menu, 0, 2, "2x2 shift-click");
        helper.assertValueEqual(countMixed(player), 1L, "2x2 shift-click inventory result count");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void craftingTableSupportsSparseNormalAndShiftClickRepair(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CraftingMenu normal = craftingTable(helper, player, 1);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.setDamageValue(mixed.getMaxDamage() - 8);
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        iron.setDamageValue(iron.getMaxDamage() - 4);
        int expectedDamage = expectedDamage(mixed, iron);
        MixedBootsData normalSources = GameTestStacks.data(mixed);
        normal.getInputGridSlots().get(0).set(mixed);
        normal.getInputGridSlots().get(8).set(iron);

        ItemStack preview = normal.getSlot(CraftingMenu.RESULT_SLOT).getItem();
        helper.assertValueEqual(preview.getDamageValue(), expectedDamage, "sparse 3x3 preview damage");
        ItemStack taken = normal.getSlot(CraftingMenu.RESULT_SLOT).safeTake(1, 1, player);
        helper.assertTrue(GameTestStacks.data(taken).equals(normalSources),
                          "Sparse 3x3 normal take changed source snapshots");
        assertConsumedOnce(helper, normal, 0, 8, "sparse 3x3 normal take");

        CraftingMenu shifted = craftingTable(helper, player, 2);
        ItemStack gold = new ItemStack(Items.GOLDEN_BOOTS);
        ItemStack reversedMixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        reversedMixed.setDamageValue(reversedMixed.getMaxDamage() - 3);
        MixedBootsData shiftedSources = GameTestStacks.data(reversedMixed);
        shifted.getInputGridSlots().get(2).set(gold);
        shifted.getInputGridSlots().get(6).set(reversedMixed);

        ItemStack moved = shifted.quickMoveStack(player, CraftingMenu.RESULT_SLOT);
        helper.assertTrue(!moved.isEmpty(), "Sparse reverse-order 3x3 repair could not be shift-clicked");
        helper.assertValueEqual(moved.getDamageValue(), 0, "saturated sparse 3x3 repair damage");
        helper.assertTrue(GameTestStacks.data(moved).equals(shiftedSources),
                          "Sparse 3x3 shift-click changed source snapshots");
        assertConsumedOnce(helper, shifted, 2, 6, "sparse 3x3 shift-click");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void craftingMenusRejectInvalidRepairInputsWithoutConsumption(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        InventoryMenu inventory = new InventoryMenu(player.getInventory(), true, player);
        assertRejected(helper, player, inventory, List.of(
                GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS),
                new ItemStack(Items.DIAMOND_BOOTS)), "unrelated donor");
        assertRejected(helper, player, inventory, List.of(
                GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS),
                GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse()), "Mixed/Mixed");
        assertRejected(helper, player, inventory, List.of(
                new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                new ItemStack(Items.IRON_BOOTS)), "bare mixed stack");
        ItemStack stackedDonor = new ItemStack(Items.IRON_BOOTS, 2);
        assertRejected(helper, player, inventory, List.of(
                GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS), stackedDonor), "stacked donor");

        CraftingMenu table = craftingTable(helper, player, 3);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        ItemStack donor = new ItemStack(Items.IRON_BOOTS);
        ItemStack extra = new ItemStack(Items.STONE);
        table.getInputGridSlots().get(0).set(mixed);
        table.getInputGridSlots().get(4).set(donor);
        table.getInputGridSlots().get(8).set(extra);
        helper.assertTrue(table.getSlot(CraftingMenu.RESULT_SLOT).getItem().isEmpty(),
                          "3x3 repair accepted an unrelated occupied slot");
        helper.assertTrue(table.quickMoveStack(player, CraftingMenu.RESULT_SLOT).isEmpty(),
                          "Rejected 3x3 repair produced a shift-click result");
        helper.assertTrue(table.getInputGridSlots().get(0).getItem() == mixed
                                  && table.getInputGridSlots().get(4).getItem() == donor
                                  && table.getInputGridSlots().get(8).getItem() == extra,
                          "Rejected 3x3 repair changed an input or unrelated slot");
        helper.succeed();
    }

    private static void assertRejected(
            GameTestHelper helper,
            ServerPlayer player,
            AbstractCraftingMenu menu,
            List<ItemStack> inputs,
            String description) {
        menu.getInputGridSlots().forEach(slot -> slot.set(ItemStack.EMPTY));
        for (int index = 0; index < inputs.size(); index++) {
            menu.getInputGridSlots().get(index).set(inputs.get(index));
        }
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), description + " exposed a repair preview");
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), description + " produced a shift-click result");
        for (int index = 0; index < inputs.size(); index++) {
            helper.assertTrue(menu.getInputGridSlots().get(index).getItem() == inputs.get(index),
                              description + " changed input " + index);
        }
    }

    private static void assertRichResult(
            GameTestHelper helper,
            ItemStack result,
            MixedBootsData sources,
            int expectedMaximum,
            int expectedDamage,
            Holder<Enchantment> ordinary,
            Holder<Enchantment> curse,
            CustomData opaque,
            String description) {
        helper.assertTrue(result.is(ModContent.MIXED_BOOTS_KEY), description + " has the wrong item");
        helper.assertValueEqual(result.getCount(), 1, description + " count");
        helper.assertValueEqual(result.getMaxDamage(), expectedMaximum, description + " maximum damage");
        helper.assertValueEqual(result.getDamageValue(), expectedDamage, description + " damage");
        ItemEnchantments enchantments = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertValueEqual(enchantments.getLevel(ordinary), 0, description + " ordinary enchantment level");
        helper.assertValueEqual(enchantments.getLevel(curse), 2, description + " curse level");
        helper.assertTrue(!result.has(DataComponents.CUSTOM_NAME), description + " retained its custom name");
        helper.assertTrue(!result.has(DataComponents.REPAIR_COST), description + " retained prior-work cost");
        helper.assertTrue(opaque.equals(result.get(DataComponents.CUSTOM_DATA)),
                          description + " lost its opaque root component");
        helper.assertTrue(GameTestStacks.data(result).equals(sources),
                          description + " changed ordered source snapshots");
    }

    private static void assertConsumedOnce(
            GameTestHelper helper,
            AbstractCraftingMenu menu,
            int firstSlot,
            int secondSlot,
            String description) {
        helper.assertTrue(menu.getInputGridSlots().get(firstSlot).getItem().isEmpty(),
                          description + " did not consume the first input exactly once");
        helper.assertTrue(menu.getInputGridSlots().get(secondSlot).getItem().isEmpty(),
                          description + " did not consume the second input exactly once");
        helper.assertTrue(menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty()),
                          description + " left an unexpected crafting remainder");
    }

    private static CraftingMenu craftingTable(GameTestHelper helper, ServerPlayer player, int containerId) {
        return new CraftingMenu(containerId, player.getInventory(), ContainerLevelAccess.create(
                helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
    }

    private static int expectedDamage(ItemStack mixed, ItemStack donor) {
        int maximum = mixed.getMaxDamage();
        int remaining = maximum - mixed.getDamageValue()
                + donor.getMaxDamage() - donor.getDamageValue()
                + maximum * 5 / 100;
        return Math.max(0, maximum - Math.min(maximum, remaining));
    }

    private static long countMixed(ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems().stream()
                     .filter(ModContent::isMixedBoots)
                     .mapToLong(ItemStack::getCount)
                     .sum();
    }

    private static Holder<Enchantment> enchantment(
            GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static void setEnchantments(
            ItemStack stack,
            Holder<Enchantment> first,
            int firstLevel,
            Holder<Enchantment> second,
            int secondLevel) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(first, firstLevel);
        enchantments.set(second, secondLevel);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    private static CustomData customData(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("value", value);
        return CustomData.of(tag);
    }
}
