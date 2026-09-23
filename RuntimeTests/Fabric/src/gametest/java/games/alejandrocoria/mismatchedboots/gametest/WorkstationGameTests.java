package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class WorkstationGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneDisenchantsMixedBootsInFirstInput(GameTestHelper helper) {
        assertSingleInputGrindstone(helper, GrindstoneMenu.INPUT_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneDisenchantsMixedBootsInSecondInput(GameTestHelper helper) {
        assertSingleInputGrindstone(helper, GrindstoneMenu.ADDITIONAL_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneCloseWithoutExtractionAwardsNoExperience(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        setEnchantment(mixed, enchantment(helper, Enchantments.UNBREAKING), 1, false);
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());
        menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(mixed);
        helper.assertTrue(!menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty(),
                "Grindstone did not preview disenchantment before close");
        menu.removed(player);

        ItemStack paired = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        paired.setDamageValue(paired.getMaxDamage() / 2);
        GrindstoneMenu repair = new GrindstoneMenu(2, player.getInventory());
        repair.getSlot(GrindstoneMenu.INPUT_SLOT).set(paired);
        repair.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_BOOTS));
        helper.assertTrue(!repair.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty(),
                "Grindstone did not preview repair before close");
        repair.removed(player);
        helper.assertValueEqual(grindstoneExperience(helper), 0, "experience after closing grindstone");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRejectsUnrelatedAndMixedDonors(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);

        GrindstoneMenu both = new GrindstoneMenu(3, player.getInventory());
        both.getSlot(GrindstoneMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        both.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(
                GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse());
        assertNoGrindstoneResult(helper, both, "mixed boots in both inputs");

        GrindstoneMenu paired = new GrindstoneMenu(4, player.getInventory());
        paired.getSlot(GrindstoneMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        paired.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND_BOOTS));
        assertNoGrindstoneResult(helper, paired, "mixed boots paired with unrelated boots");

        GrindstoneMenu reversed = new GrindstoneMenu(5, player.getInventory());
        reversed.getSlot(GrindstoneMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_BOOTS));
        reversed.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        assertNoGrindstoneResult(helper, reversed, "unrelated boots paired with mixed boots");

        helper.assertTrue(helper.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.EXPERIENCE_ORB,
                helper.getBounds(), net.minecraft.world.entity.Entity::isAlive).isEmpty(),
                "Rejected grindstone input created experience");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRepairsWithLeftSourceInFirstInput(GameTestHelper helper) {
        assertOrdinaryGrindstoneDonor(helper, Items.IRON_BOOTS, GrindstoneMenu.INPUT_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRepairsWithLeftSourceInSecondInput(GameTestHelper helper) {
        assertOrdinaryGrindstoneDonor(helper, Items.IRON_BOOTS, GrindstoneMenu.ADDITIONAL_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRepairsWithRightSourceInFirstInput(GameTestHelper helper) {
        assertOrdinaryGrindstoneDonor(helper, Items.GOLDEN_BOOTS, GrindstoneMenu.INPUT_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRepairsWithRightSourceInSecondInput(GameTestHelper helper) {
        assertOrdinaryGrindstoneDonor(helper, Items.GOLDEN_BOOTS, GrindstoneMenu.ADDITIONAL_SLOT);
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRepairsUnenchantedMixedBootsWithoutExperience(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.setDamageValue(mixed.getMaxDamage() * 3 / 4);
        ItemStack donor = new ItemStack(Items.GOLDEN_BOOTS);
        donor.setDamageValue(donor.getMaxDamage() - 1);
        GrindstoneMenu menu = grindstone(helper, player, 1);
        menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(mixed);
        menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(donor);

        ItemStack preview = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!preview.isEmpty(), "Unenchanted mixed boots could not be repaired");
        helper.assertValueEqual(preview.getMaxDamage(), mixed.getMaxDamage(), "unenchanted repair maximum");
        ItemStack taken = menu.quickMoveStack(player, GrindstoneMenu.RESULT_SLOT);
        helper.assertTrue(!taken.isEmpty(), "Unenchanted grindstone repair could not be extracted");
        helper.assertValueEqual(grindstoneExperience(helper), 0, "unenchanted repair experience");
        helper.succeed();
    }

    private static void assertOrdinaryGrindstoneDonor(
            GameTestHelper helper, Item donorItem, int mixedSlot) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        Holder<Enchantment> protection = enchantment(helper, Enchantments.PROTECTION);
        Holder<Enchantment> curse = enchantment(helper, Enchantments.VANISHING_CURSE);
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        setEnchantment(left, protection, 1, false);
        setEnchantment(right, protection, 1, false);
        ItemStack mixed = GameTestStacks.pair(left, right).primary();
        MixedBootsData sources = GameTestStacks.data(mixed);
        int rootMaximum = mixed.getMaxDamage();
        mixed.setDamageValue(rootMaximum * 3 / 4);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Repair target"));
        mixed.set(DataComponents.REPAIR_COST, 17);
        ItemStack donor = new ItemStack(donorItem);
        donor.setDamageValue(donor.getMaxDamage() - 10);
        ItemEnchantments.Mutable donorEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        donorEnchantments.set(protection, 4);
        donorEnchantments.set(curse, 1);
        donor.set(DataComponents.ENCHANTMENTS, donorEnchantments.toImmutable());

        GrindstoneMenu menu = grindstone(helper, player, mixedSlot + 1);
        menu.getSlot(mixedSlot).set(mixed);
        menu.getSlot(mixedSlot == GrindstoneMenu.INPUT_SLOT
                ? GrindstoneMenu.ADDITIONAL_SLOT : GrindstoneMenu.INPUT_SLOT).set(donor);
        ItemStack preview = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        int expectedDamage = Math.max(0, rootMaximum - (rootMaximum - mixed.getDamageValue()
                + donor.getMaxDamage() - donor.getDamageValue() + rootMaximum * 5 / 100));
        helper.assertTrue(!preview.isEmpty(), "Grindstone rejected source donor " + donorItem);
        helper.assertTrue(preview.is(mixed.getItem()), "Grindstone returned the ordinary donor");
        helper.assertValueEqual(preview.getMaxDamage(), rootMaximum, "grindstone repair maximum");
        helper.assertValueEqual(preview.getDamageValue(), expectedDamage, "grindstone repair damage");
        helper.assertTrue(GameTestStacks.data(preview).equals(sources), "Grindstone changed source snapshots");
        helper.assertTrue(preview.getHoverName().equals(Component.literal("Repair target")),
                "Grindstone changed the target's custom name");
        ItemEnchantments resultEnchantments = preview.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertValueEqual(resultEnchantments.getLevel(protection), 0, "removed donor Protection level");
        helper.assertValueEqual(resultEnchantments.getLevel(curse), 1, "retained donor curse level");
        helper.assertValueEqual(preview.getOrDefault(DataComponents.REPAIR_COST, 0), 1,
                "grindstone donor prior-work cost");
        helper.assertValueEqual(grindstoneExperience(helper), 0, "experience before donor extraction");

        ItemStack taken = menu.quickMoveStack(player, GrindstoneMenu.RESULT_SLOT);
        helper.assertTrue(!taken.isEmpty(), "Grindstone donor result could not be extracted");
        helper.assertValueEqual(taken.getMaxDamage(), rootMaximum, "extracted donor repair maximum");
        helper.assertValueEqual(taken.getDamageValue(), expectedDamage, "extracted donor repair damage");
        helper.assertTrue(GameTestStacks.data(taken).equals(sources), "Donor extraction changed snapshots");
        helper.assertTrue(menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem().isEmpty(),
                "Grindstone did not consume its first input");
        helper.assertTrue(menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Grindstone did not consume its second input");
        int totalPotential = 1 + protection.value().getMinCost(4);
        int minimumXp = (totalPotential + 1) / 2;
        int experience = grindstoneExperience(helper);
        helper.assertTrue(experience >= minimumXp && experience < minimumXp * 2,
                "Ordinary donor did not contribute full XP potential: " + experience);
        helper.succeed();
    }

    private static void assertSingleInputGrindstone(GameTestHelper helper, int inputSlot) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        Holder<Enchantment> protection = enchantment(helper, Enchantments.PROTECTION);
        Holder<Enchantment> curse = enchantment(helper, Enchantments.VANISHING_CURSE);
        ItemStack left = new ItemStack(Items.IRON_BOOTS);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        setEnchantments(left, protection, curse);
        setEnchantment(right, protection, 1, false);
        ItemStack mixed = GameTestStacks.pair(left, right).primary();
        MixedBootsData sources = GameTestStacks.data(mixed);
        helper.assertValueEqual(mixed.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(protection), 2, "merged Protection level");
        int originalDamage = Math.min(7, mixed.getMaxDamage() - 1);
        mixed.setDamageValue(originalDamage);
        mixed.set(DataComponents.CUSTOM_NAME, Component.literal("Custom pair"));
        mixed.set(DataComponents.REPAIR_COST, 13);

        GrindstoneMenu menu = grindstone(helper, player, inputSlot + 1);
        menu.getSlot(inputSlot).set(mixed);
        ItemStack preview = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!preview.isEmpty(), "Grindstone did not preview mixed boots in slot " + inputSlot);
        ItemEnchantments resultEnchantments = preview.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertValueEqual(resultEnchantments.getLevel(protection), 0, "removed Protection level");
        helper.assertValueEqual(resultEnchantments.getLevel(curse), 1, "preserved curse level");
        helper.assertValueEqual(preview.getDamageValue(), originalDamage, "grindstone preview damage");
        helper.assertTrue(preview.getHoverName().equals(Component.literal("Custom pair")),
                "Grindstone changed the custom name");
        helper.assertValueEqual(preview.getOrDefault(DataComponents.REPAIR_COST, 0), 1,
                "grindstone preview prior-work cost");
        helper.assertTrue(GameTestStacks.data(preview).equals(sources), "Grindstone changed source snapshots");
        helper.assertValueEqual(grindstoneExperience(helper), 0, "experience before extraction");

        ItemStack taken = menu.quickMoveStack(player, GrindstoneMenu.RESULT_SLOT);
        helper.assertTrue(!taken.isEmpty(), "Grindstone shift-click did not extract the result");
        helper.assertTrue(GameTestStacks.data(taken).equals(sources), "Extraction changed source snapshots");
        helper.assertValueEqual(taken.getDamageValue(), originalDamage, "extracted grindstone damage");
        helper.assertTrue(menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem().isEmpty(),
                "Grindstone did not clear the first input");
        helper.assertTrue(menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Grindstone did not clear the second input");
        helper.assertValueEqual(grindstoneExperience(helper), 1,
                "bounded experience from two Protection I sources");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void ordinaryGrindstoneOperationStillWorks(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack enchanted = new ItemStack(Items.IRON_BOOTS);
        setEnchantment(enchanted, enchantment(helper, Enchantments.UNBREAKING), 1, false);
        GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory());

        menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(enchanted);

        ItemStack result = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        helper.assertTrue(result.is(Items.IRON_BOOTS), "Ordinary grindstone operation produced no result");
        helper.assertTrue(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                "Ordinary grindstone result retained a removable enchantment");

        ItemStack first = new ItemStack(Items.IRON_BOOTS);
        ItemStack second = new ItemStack(Items.IRON_BOOTS);
        first.setDamageValue(first.getMaxDamage() - 20);
        second.setDamageValue(second.getMaxDamage() - 10);
        GrindstoneMenu repair = new GrindstoneMenu(2, player.getInventory());
        repair.getSlot(GrindstoneMenu.INPUT_SLOT).set(first);
        repair.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(second);
        ItemStack repaired = repair.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        helper.assertTrue(repaired.is(Items.IRON_BOOTS), "Ordinary grindstone repair produced no result");
        helper.assertValueEqual(repaired.getDamageValue(),
                first.getMaxDamage() - (20 + 10 + first.getMaxDamage() * 5 / 100),
                "ordinary grindstone repair damage");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilRenameAndRenameRemovalPreserveMixedData(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack original = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData originalData = GameTestStacks.data(original);
        AnvilMenu rename = new AnvilMenu(1, player.getInventory());
        rename.getSlot(AnvilMenu.INPUT_SLOT).set(original);
        rename.setItemName("Test name");
        ItemStack renamed = rename.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(renamed.getHoverName().equals(Component.literal("Test name")), "Anvil rename was not applied");
        helper.assertTrue(GameTestStacks.data(renamed).equals(originalData), "Anvil rename changed source snapshots");

        AnvilMenu remove = new AnvilMenu(2, player.getInventory());
        remove.getSlot(AnvilMenu.INPUT_SLOT).set(renamed);
        remove.setItemName("");
        ItemStack unnamed = remove.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!unnamed.has(DataComponents.CUSTOM_NAME), "Anvil did not remove the custom name");
        helper.assertTrue(GameTestStacks.data(unnamed).equals(originalData), "Name removal changed source snapshots");

        AnvilMenu control = new AnvilMenu(3, player.getInventory());
        control.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.IRON_BOOTS));
        control.setItemName("Ordinary name");
        ItemStack ordinary = control.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(ordinary.is(Items.IRON_BOOTS), "Ordinary anvil rename produced no result");
        helper.assertTrue(ordinary.getHoverName().equals(Component.literal("Ordinary name")),
                "Ordinary anvil rename was not applied");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilUsesEitherSourceForEnchantmentCompatibility(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack swordAsBoot = GameTestStacks.feetItem(Items.IRON_SWORD);
        ItemStack mixed = GameTestStacks.pair(swordAsBoot, new ItemStack(Items.IRON_BOOTS)).primary();
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        AnvilMenu compatible = new AnvilMenu(1, player.getInventory());
        compatible.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        compatible.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(enchantedBook(sharpness));
        ItemStack accepted = compatible.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!accepted.isEmpty(), "Anvil rejected an enchantment supported by one source");
        helper.assertValueEqual(accepted.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(sharpness), 1, "accepted Sharpness level");
        helper.assertTrue(GameTestStacks.data(accepted).equals(GameTestStacks.data(mixed)),
                "Anvil book application changed source snapshots");

        AnvilMenu ordinaryControl = new AnvilMenu(2, player.getInventory());
        ordinaryControl.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.IRON_SWORD));
        ordinaryControl.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(enchantedBook(sharpness));
        ItemStack ordinaryAccepted = ordinaryControl.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(ordinaryAccepted.is(Items.IRON_SWORD),
                "Ordinary anvil enchantment application produced no result");
        helper.assertValueEqual(ordinaryAccepted
                .getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness),
                1, "ordinary accepted Sharpness level");

        Holder<Enchantment> bane = enchantment(helper, Enchantments.BANE_OF_ARTHROPODS);
        AnvilMenu incompatible = new AnvilMenu(3, player.getInventory());
        incompatible.getSlot(AnvilMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        incompatible.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(enchantedBook(bane));
        helper.assertTrue(incompatible.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil accepted an enchantment unsupported by both sources");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilRepairsWithEitherSourceMaterial(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.giveExperienceLevels(30);

        assertMaterialRepair(helper, player, Items.IRON_INGOT, 1);
        assertMaterialRepair(helper, player, Items.GOLD_INGOT, 2);

        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.setDamageValue(Math.max(1, mixed.getMaxDamage() / 4));
        AnvilMenu unrelated = new AnvilMenu(3, player.getInventory());
        unrelated.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        unrelated.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND));
        helper.assertTrue(unrelated.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil repaired mixed boots with an unrelated material");

        ItemStack ordinary = new ItemStack(Items.IRON_BOOTS);
        ordinary.setDamageValue(Math.max(1, ordinary.getMaxDamage() / 4));
        AnvilMenu control = new AnvilMenu(4, player.getInventory());
        control.getSlot(AnvilMenu.INPUT_SLOT).set(ordinary);
        control.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(control.getSlot(AnvilMenu.RESULT_SLOT).getItem().is(Items.IRON_BOOTS),
                "Ordinary material repair stopped working");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilRepairsWithEitherOrdinarySource(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.giveExperienceLevels(30);
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);

        assertOrdinaryDonorRepair(helper, player, Items.IRON_BOOTS, unbreaking, 1);
        assertOrdinaryDonorRepair(helper, player, Items.GOLDEN_BOOTS, unbreaking, 2);
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilAppliesVanillaCompatibilityToOrdinaryDonorEnchantments(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        Holder<Enchantment> frostWalker = enchantment(helper, Enchantments.FROST_WALKER);
        Holder<Enchantment> depthStrider = enchantment(helper, Enchantments.DEPTH_STRIDER);
        Holder<Enchantment> unbreaking = enchantment(helper, Enchantments.UNBREAKING);

        ItemStack frostSource = new ItemStack(Items.IRON_BOOTS);
        setEnchantment(frostSource, frostWalker, 1, false);
        ItemStack rejectsDepth = GameTestStacks.pair(frostSource, new ItemStack(Items.GOLDEN_BOOTS)).primary();
        MixedBootsData rejectsDepthData = GameTestStacks.data(rejectsDepth);
        rejectsDepth.setDamageValue(Math.max(1, rejectsDepth.getMaxDamage() * 3 / 4));
        ItemStack depthDonor = new ItemStack(Items.IRON_BOOTS);
        depthDonor.setDamageValue(Math.max(1, depthDonor.getMaxDamage() - 10));
        setEnchantments(depthDonor, depthStrider, unbreaking);
        AnvilMenu incompatible = new AnvilMenu(1, player.getInventory());
        incompatible.getSlot(AnvilMenu.INPUT_SLOT).set(rejectsDepth);
        incompatible.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(depthDonor);

        ItemStack incompatibleResult = incompatible.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!incompatibleResult.isEmpty(),
                "Anvil discarded the donor's valid durability repair");
        ItemEnchantments incompatibleEnchantments = incompatibleResult.getOrDefault(
                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertValueEqual(incompatibleEnchantments.getLevel(frostWalker), 1,
                "retained Frost Walker level");
        helper.assertValueEqual(incompatibleEnchantments.getLevel(depthStrider), 0,
                "rejected incompatible Depth Strider level");
        helper.assertValueEqual(incompatibleEnchantments.getLevel(unbreaking), 1,
                "accepted compatible Unbreaking level");
        helper.assertTrue(GameTestStacks.data(incompatibleResult).equals(rejectsDepthData),
                "Incompatible donor changed source snapshots");

        ItemStack frost = new ItemStack(Items.IRON_BOOTS);
        setEnchantment(frost, frostWalker, 1, false);
        ItemStack depth = new ItemStack(Items.GOLDEN_BOOTS);
        setEnchantment(depth, depthStrider, 1, false);
        ItemStack keepsExistingConflict = GameTestStacks.pair(frost, depth).primary();
        keepsExistingConflict.setDamageValue(Math.max(1, keepsExistingConflict.getMaxDamage() * 3 / 4));
        ItemStack compatibleDonor = new ItemStack(Items.IRON_BOOTS);
        compatibleDonor.setDamageValue(Math.max(1, compatibleDonor.getMaxDamage() - 10));
        setEnchantment(compatibleDonor, unbreaking, 1, false);
        AnvilMenu compatible = new AnvilMenu(2, player.getInventory());
        compatible.getSlot(AnvilMenu.INPUT_SLOT).set(keepsExistingConflict);
        compatible.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(compatibleDonor);

        ItemEnchantments compatibleEnchantments = compatible.getSlot(AnvilMenu.RESULT_SLOT).getItem()
                .getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertValueEqual(compatibleEnchantments.getLevel(frostWalker), 1,
                "existing Frost Walker level");
        helper.assertValueEqual(compatibleEnchantments.getLevel(depthStrider), 1,
                "existing Depth Strider level");
        helper.assertValueEqual(compatibleEnchantments.getLevel(unbreaking), 1,
                "compatible donor Unbreaking level");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void anvilRejectsUnsupportedBootCombinations(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.setDamageValue(Math.max(1, mixed.getMaxDamage() * 3 / 4));

        ItemStack inverse = GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse();
        AnvilMenu combine = new AnvilMenu(1, player.getInventory());
        combine.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        combine.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(inverse);
        helper.assertTrue(combine.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined two mixed orientations");

        AnvilMenu sameOrientation = new AnvilMenu(2, player.getInventory());
        sameOrientation.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        sameOrientation.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(mixed.copy());
        helper.assertTrue(sameOrientation.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined the same mixed orientation");

        AnvilMenu unrelated = new AnvilMenu(3, player.getInventory());
        unrelated.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        unrelated.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(
                GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.LEATHER_BOOTS));
        helper.assertTrue(unrelated.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined unrelated mixed boots");

        ItemStack diamondDonor = new ItemStack(Items.DIAMOND_BOOTS);
        diamondDonor.setDamageValue(Math.max(1, diamondDonor.getMaxDamage() - 10));
        AnvilMenu ordinaryUnrelated = new AnvilMenu(4, player.getInventory());
        ordinaryUnrelated.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        ordinaryUnrelated.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(diamondDonor);
        helper.assertTrue(ordinaryUnrelated.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil repaired mixed boots with an unrelated ordinary donor");

        ItemStack ordinaryTarget = new ItemStack(Items.IRON_BOOTS);
        ordinaryTarget.setDamageValue(Math.max(1, ordinaryTarget.getMaxDamage() * 3 / 4));
        AnvilMenu reverse = new AnvilMenu(5, player.getInventory());
        reverse.getSlot(AnvilMenu.INPUT_SLOT).set(ordinaryTarget);
        reverse.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(mixed.copy());
        helper.assertTrue(reverse.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil accepted mixed boots as a donor for ordinary boots");

        ItemStack ordinaryDonor = new ItemStack(Items.IRON_BOOTS);
        ordinaryDonor.setDamageValue(Math.max(1, ordinaryDonor.getMaxDamage() - 10));
        AnvilMenu ordinaryControl = new AnvilMenu(6, player.getInventory());
        ordinaryControl.getSlot(AnvilMenu.INPUT_SLOT).set(ordinaryTarget.copy());
        ordinaryControl.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(ordinaryDonor);
        helper.assertTrue(ordinaryControl.getSlot(AnvilMenu.RESULT_SLOT).getItem().is(Items.IRON_BOOTS),
                "Ordinary boot combination stopped working");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void smithingTransformsMixedBootsWithoutBreakingVanillaRecipes(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.IRON_BOOTS);

        SmithingMenu upgrade = smithing(helper, player,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), mixed, new ItemStack(Items.NETHERITE_INGOT));
        ItemStack upgradedMixed = upgrade.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(upgradedMixed.is(mixed.getItem())
                        && GameTestStacks.data(upgradedMixed).left().source().is(Items.NETHERITE_BOOTS)
                        && GameTestStacks.data(upgradedMixed).right().source().is(Items.IRON_BOOTS),
                "Smithing upgrade did not transform the eligible mixed boot source");

        SmithingMenu trim = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), mixed, new ItemStack(Items.DIAMOND));
        ItemStack trimmedMixed = trim.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(trimmedMixed.is(mixed.getItem())
                        && GameTestStacks.data(trimmedMixed).left().source().has(DataComponents.TRIM)
                        && GameTestStacks.data(trimmedMixed).right().source().has(DataComponents.TRIM),
                "Smithing trim did not transform both mixed boot sources");

        SmithingMenu upgradeMixedTemplate = smithing(helper, player,
                mixed, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.NETHERITE_INGOT));
        SmithingMenu upgradeMixedAddition = smithing(helper, player,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_BOOTS), mixed);
        helper.assertTrue(upgradeMixedTemplate.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing upgrade accepted mixed boots as a template");
        helper.assertTrue(upgradeMixedAddition.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing upgrade accepted mixed boots as an addition");

        SmithingMenu trimMixedTemplate = smithing(helper, player,
                mixed, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND));
        SmithingMenu trimMixedAddition = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_BOOTS), mixed);
        helper.assertTrue(trimMixedTemplate.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing trim accepted mixed boots as a template");
        helper.assertTrue(trimMixedAddition.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing trim accepted mixed boots as an addition");

        SmithingMenu control = smithing(helper, player,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(control.getSlot(SmithingMenu.RESULT_SLOT).getItem().is(Items.NETHERITE_BOOTS),
                "Ordinary Netherite upgrade stopped working");

        SmithingMenu trimControl = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND));
        ItemStack trimmed = trimControl.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        helper.assertTrue(trimmed.is(Items.DIAMOND_BOOTS) && trimmed.has(DataComponents.TRIM),
                "Ordinary armor trim stopped working");
        helper.succeed();
    }

    private static void assertNoGrindstoneResult(
            GameTestHelper helper, GrindstoneMenu menu, String scenario) {
        helper.assertTrue(menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty(),
                "Grindstone accepted " + scenario);
    }

    private static GrindstoneMenu grindstone(
            GameTestHelper helper, net.minecraft.world.entity.player.Player player, int menuId) {
        return new GrindstoneMenu(menuId, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
    }

    private static int grindstoneExperience(GameTestHelper helper) {
        return helper.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.EXPERIENCE_ORB,
                        helper.getBounds(), net.minecraft.world.entity.Entity::isAlive).stream()
                .mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
    }

    private static void assertMaterialRepair(
            GameTestHelper helper,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.item.Item material,
            int menuId) {
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData originalData = GameTestStacks.data(mixed);
        int originalDamage = Math.max(1, mixed.getMaxDamage() / 4);
        mixed.setDamageValue(originalDamage);
        AnvilMenu menu = new AnvilMenu(menuId, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(material, 2));

        ItemStack preview = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!preview.isEmpty(), "Anvil rejected source repair material " + material);
        helper.assertValueEqual(preview.getDamageValue(), 0, "material repair preview damage");
        helper.assertTrue(GameTestStacks.data(preview).equals(originalData),
                "Material repair preview changed source snapshots");
        int cost = menu.getCost();
        helper.assertTrue(cost > 0, "Material repair had no experience cost");
        int levelsBefore = player.experienceLevel;

        ItemStack taken = menu.quickMoveStack(player, AnvilMenu.RESULT_SLOT);

        helper.assertTrue(!taken.isEmpty(), "Material repair result could not be extracted");
        helper.assertValueEqual(taken.getDamageValue(), 0, "extracted material repair damage");
        helper.assertTrue(GameTestStacks.data(taken).equals(originalData),
                "Extracted material repair changed source snapshots");
        helper.assertTrue(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(),
                "Material repair did not consume the mixed input");
        helper.assertValueEqual(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount(), 1,
                "material items remaining after repair");
        helper.assertValueEqual(player.experienceLevel, levelsBefore - cost,
                "experience levels after material repair");
    }

    private static void assertOrdinaryDonorRepair(
            GameTestHelper helper,
            net.minecraft.world.entity.player.Player player,
            Item donorItem,
            Holder<Enchantment> donorEnchantment,
            int menuId) {
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData originalData = GameTestStacks.data(mixed);
        int originalMaximum = mixed.getMaxDamage();
        int originalDamage = Math.max(1, originalMaximum * 3 / 4);
        mixed.setDamageValue(originalDamage);
        ItemStack donor = new ItemStack(donorItem);
        int donorDamage = Math.max(1, donor.getMaxDamage() - 10);
        donor.setDamageValue(donorDamage);
        setEnchantment(donor, donorEnchantment, 1, false);
        AnvilMenu menu = new AnvilMenu(menuId, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(donor);

        ItemStack preview = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        int expectedRemaining = originalMaximum - originalDamage
                + donor.getMaxDamage() - donorDamage
                + originalMaximum * 12 / 100;
        int expectedDamage = Math.max(0, originalMaximum - expectedRemaining);
        helper.assertTrue(!preview.isEmpty(), "Anvil rejected ordinary source donor " + donorItem);
        helper.assertValueEqual(preview.getMaxDamage(), originalMaximum, "ordinary donor result maximum");
        helper.assertValueEqual(preview.getDamageValue(), expectedDamage, "ordinary donor result damage");
        helper.assertValueEqual(preview.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(donorEnchantment), 1, "ordinary donor enchantment level");
        helper.assertValueEqual(preview.getOrDefault(DataComponents.REPAIR_COST, 0), 1,
                "ordinary donor prior-work cost");
        helper.assertTrue(GameTestStacks.data(preview).equals(originalData),
                "Ordinary donor preview changed source snapshots");
        int cost = menu.getCost();
        helper.assertTrue(cost > 0, "Ordinary donor repair had no experience cost");
        int levelsBefore = player.experienceLevel;

        ItemStack taken = menu.quickMoveStack(player, AnvilMenu.RESULT_SLOT);

        helper.assertTrue(!taken.isEmpty(), "Ordinary donor result could not be extracted");
        helper.assertValueEqual(taken.getDamageValue(), expectedDamage, "extracted ordinary donor damage");
        helper.assertTrue(GameTestStacks.data(taken).equals(originalData),
                "Extracted ordinary donor changed source snapshots");
        helper.assertTrue(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(),
                "Ordinary donor repair did not consume the mixed input");
        helper.assertTrue(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(),
                "Ordinary donor repair did not consume the donor");
        helper.assertValueEqual(player.experienceLevel, levelsBefore - cost,
                "experience levels after ordinary donor repair");
    }

    private static SmithingMenu smithing(
            GameTestHelper helper,
            net.minecraft.world.entity.player.Player player,
            ItemStack template,
            ItemStack base,
            ItemStack addition) {
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        return menu;
    }

    private static Holder<Enchantment> enchantment(
            GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack enchantedBook(Holder<Enchantment> enchantment) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        setEnchantment(book, enchantment, 1, true);
        return book;
    }

    private static void setEnchantment(
            ItemStack stack, Holder<Enchantment> enchantment, int level, boolean stored) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        stack.set(stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
                enchantments.toImmutable());
    }

    private static void setEnchantments(
            ItemStack stack,
            Holder<Enchantment> first,
            Holder<Enchantment> second) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(first, 1);
        enchantments.set(second, 1);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }
}
