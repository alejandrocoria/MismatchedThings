package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class WorkstationGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void grindstoneRejectsMixedBootsInEitherInput(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        GrindstoneMenu first = new GrindstoneMenu(1, player.getInventory());
        first.getSlot(GrindstoneMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        assertNoGrindstoneResult(helper, first, "mixed boots in first input");

        GrindstoneMenu second = new GrindstoneMenu(2, player.getInventory());
        second.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        assertNoGrindstoneResult(helper, second, "mixed boots in second input");

        GrindstoneMenu both = new GrindstoneMenu(3, player.getInventory());
        both.getSlot(GrindstoneMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        both.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(
                GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse());
        assertNoGrindstoneResult(helper, both, "mixed boots in both inputs");

        GrindstoneMenu paired = new GrindstoneMenu(4, player.getInventory());
        paired.getSlot(GrindstoneMenu.INPUT_SLOT).set(GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        paired.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_BOOTS));
        assertNoGrindstoneResult(helper, paired, "mixed boots paired with ordinary boots");

        ItemStack modified = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        modified.setDamageValue(Math.min(5, modified.getMaxDamage() - 1));
        setEnchantment(modified, enchantment(helper, Enchantments.UNBREAKING), 1, false);
        GrindstoneMenu damagedAndEnchanted = new GrindstoneMenu(5, player.getInventory());
        damagedAndEnchanted.getSlot(GrindstoneMenu.INPUT_SLOT).set(modified);
        assertNoGrindstoneResult(helper, damagedAndEnchanted, "damaged and enchanted mixed boots");

        helper.assertTrue(helper.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.EXPERIENCE_ORB,
                helper.getBounds(), net.minecraft.world.entity.Entity::isAlive).isEmpty(),
                "Rejected grindstone input created experience");
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
    public void anvilRejectsRepairsAndMixedCombinations(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        mixed.setDamageValue(Math.min(5, mixed.getMaxDamage()));

        AnvilMenu repair = new AnvilMenu(1, player.getInventory());
        repair.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        repair.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(repair.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil repaired mixed boots with an owner material");

        ItemStack inverse = GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse();
        AnvilMenu combine = new AnvilMenu(2, player.getInventory());
        combine.getSlot(AnvilMenu.INPUT_SLOT).set(mixed);
        combine.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(inverse);
        helper.assertTrue(combine.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined two mixed orientations");

        AnvilMenu sameOrientation = new AnvilMenu(3, player.getInventory());
        sameOrientation.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        sameOrientation.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(mixed.copy());
        helper.assertTrue(sameOrientation.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined the same mixed orientation");

        AnvilMenu unrelated = new AnvilMenu(4, player.getInventory());
        unrelated.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        unrelated.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(
                GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.LEATHER_BOOTS));
        helper.assertTrue(unrelated.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil combined unrelated mixed boots");

        AnvilMenu ordinarySacrifice = new AnvilMenu(5, player.getInventory());
        ordinarySacrifice.getSlot(AnvilMenu.INPUT_SLOT).set(mixed.copy());
        ordinarySacrifice.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.IRON_BOOTS));
        helper.assertTrue(ordinarySacrifice.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
                "Anvil repaired mixed boots with ordinary boots");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void smithingRejectsMixedBootsWithoutBreakingVanillaRecipes(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack mixed = GameTestStacks.mixed(Items.DIAMOND_BOOTS, Items.IRON_BOOTS);

        SmithingMenu upgrade = smithing(helper, player,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), mixed, new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(upgrade.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing upgrade accepted mixed boots as its base");

        SmithingMenu trim = smithing(helper, player,
                new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), mixed, new ItemStack(Items.DIAMOND));
        helper.assertTrue(trim.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(),
                "Smithing trim accepted mixed boots as its base");

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
}
