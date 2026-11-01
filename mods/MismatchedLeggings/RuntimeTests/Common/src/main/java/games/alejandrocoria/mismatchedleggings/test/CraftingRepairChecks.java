package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsRepairRecipe;
import games.alejandrocoria.mismatchedleggings.test.mixin.CrafterInvoker;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class CraftingRepairChecks {
    static Consumer<MinecraftServer> nativeEligibilityCheck = server -> {};

    private CraftingRepairChecks() {
    }

    static void verify(MinecraftServer server) {
        for (boolean inverse : List.of(false, true)) {
            for (boolean table : List.of(false, true)) {
                for (var item : List.of(Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS)) {
                    var player = RepairFixtures.player(server);
                    var root = RepairFixtures.mixed(inverse);
                    var donor = new ItemStack(item);
                    donor.setDamageValue(donor.getMaxDamage() - 10);
                    var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    var unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
                    var curse = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
                    root.enchant(unbreaking, 3);
                    root.enchant(curse, 1);
                    donor.enchant(curse, 2);
                    var original = root.copy();
                    var addition = donor.copy();
                    var menu = menu(server, player, table);
                    int last = table ? 8 : 3;
                    menu.getInputGridSlots().get(0).set(inverse ? donor : root);
                    menu.getInputGridSlots().get(last).set(inverse ? root : donor);
                    var preview = menu.getSlot(0).getItem();
                    RepairFixtures.preserved(original, preview);
                    int expected = expectedDamage(root, donor);
                    require(preview.getDamageValue() == expected && !preview.has(DataComponents.CUSTOM_NAME)
                                    && !preview.has(DataComponents.REPAIR_COST)
                                    && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, preview) == 0
                                    && EnchantmentHelper.getItemEnchantmentLevel(curse, preview) == 2,
                            "Crafting repair did not apply damage/curse/name/prior-work rules");
                    require(ItemStack.matches(original, root) && ItemStack.matches(addition, donor), "Crafting preview mutated inputs");
                    int before = player.experienceLevel;
                    var snapshot = preview.copy();
                    var taken = inverse ? menu.quickMoveStack(player, 0) : menu.getSlot(0).safeTake(1, 1, player);
                    require(ItemStack.matches(snapshot, taken) && player.experienceLevel == before
                                    && menu.getInputGridSlots().stream().noneMatch(slot -> slot.hasItem())
                                    && menu.getSlot(0).safeTake(1, 1, player).isEmpty(),
                            "Crafting extraction lost output, paid XP or consumed inputs more than once");
                }
            }
        }
        invalid(server);
        controls(server);
        nativeEligibilityCheck.accept(server);
        crafter(server);
    }

    private static void invalid(MinecraftServer server) {
        var root = RepairFixtures.mixed(false);
        var donor = new ItemStack(Items.IRON_LEGGINGS);
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        for (var inputs : List.of(
                List.of(root, RepairFixtures.mixed(true)),
                List.of(root, new ItemStack(Items.DIAMOND_LEGGINGS)),
                List.of(bare, donor), List.of(malformed, donor),
                List.of(root.copyWithCount(2), donor), List.of(root, donor.copyWithCount(2)),
                List.of(root, donor, new ItemStack(Items.STONE)))) {
            var input = CraftingInput.of(3, 1, inputs.size() == 3 ? inputs : List.of(inputs.get(0), inputs.get(1), ItemStack.EMPTY));
            var snapshots = input.items().stream().map(ItemStack::copy).toList();
            var recipe = server.overworld().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, server.overworld());
            require(recipe.isEmpty(), "Invalid crafting repair found a loaded recipe");
            require(!RepairItemRecipe.INSTANCE.matches(input, server.overworld()), "Native repair bypass accepted invalid mixed input");
            for (int index = 0; index < snapshots.size(); index++) {
                require(ItemStack.matches(snapshots.get(index), input.getItem(index)), "Rejected recipe changed an input");
            }
        }
        var menu = menu(server, RepairFixtures.player(server), false);
        menu.getInputGridSlots().get(0).set(root);
        menu.getInputGridSlots().get(1).set(RepairFixtures.mixed(true));
        require(!menu.getSlot(0).hasItem() && menu.quickMoveStack(RepairFixtures.player(server), 0).isEmpty(),
                "Mixed/Mixed bypassed native crafting guard");
    }

    private static void controls(MinecraftServer server) {
        var level = server.overworld();
        var ordinary = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)));
        require(RepairItemRecipe.INSTANCE.matches(ordinary, level), "Native ordinary repair was suppressed");
        var saturated = RepairFixtures.mixed(false);
        var fullDonor = new ItemStack(Items.IRON_LEGGINGS);
        fullDonor.set(DataComponents.MAX_DAMAGE, 1000);
        var input = CraftingInput.of(2, 1, List.of(saturated, fullDonor));
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        require(recipe.value() instanceof MixedLeggingsRepairRecipe, "Loaded dedicated repair recipe is missing");
        var result = recipe.value().assemble(input);
        require(result.getMaxDamage() == saturated.getMaxDamage() && result.getDamageValue() == 0,
                "Crafting imported donor capacity or failed to clamp remaining durability");
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.mixed();
            var source = new ItemStack(Items.GOLDEN_BOOTS);
            var control = CraftingInput.of(2, 1, List.of(boots, source));
            var bootsRecipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, control, level).orElseThrow();
            var repaired = bootsRecipe.value().assemble(control);
            require(repaired.getItem() == boots.getItem() && !MixedLeggingsStructure.isMixedItem(repaired)
                            && !RepairItemRecipe.INSTANCE.matches(control, level), "Boots repair/bypass guard was suppressed");
            var cross = CraftingInput.of(2, 1, List.of(saturated, boots));
            require(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, cross, level).isEmpty(), "Cross-product repair was admitted");
        }
    }

    private static void crafter(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(10, 65, 10);
        var output = pos.south();
        level.setBlock(pos, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP), 3);
        level.setBlock(output, Blocks.BARREL.defaultBlockState(), 3);
        var crafter = (CrafterBlockEntity) level.getBlockEntity(pos);
        var barrel = (Container) level.getBlockEntity(output);
        barrel.clearContent();
        for (int slot = 1; slot < 8; slot++) {
            crafter.setSlotState(slot, false);
        }
        for (boolean inverse : List.of(false, true)) {
            var root = RepairFixtures.mixed(inverse);
            var donor = new ItemStack(inverse ? Items.GOLDEN_LEGGINGS : Items.IRON_LEGGINGS);
            donor.setDamageValue(donor.getMaxDamage() - 4);
            int expected = expectedDamage(root, donor);
            var original = root.copy();
            crafter.setItem(0, inverse ? donor : root);
            crafter.setItem(8, inverse ? root : donor);
            ((CrafterInvoker) Blocks.CRAFTER).leggings$dispense(level.getBlockState(pos), level, pos);
            require(crafter.getItems().stream().allMatch(ItemStack::isEmpty), "Crafter did not consume each repair input once");
            int count = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                var result = barrel.getItem(slot);
                if (!result.isEmpty()) {
                    count++;
                    RepairFixtures.preserved(original, result);
                    require(result.getDamageValue() == expected, "Crafter repair damage differs from ordinary crafting");
                }
            }
            require(count == 1, "Crafter output missing or duplicated");
            barrel.clearContent();
        }
        crafter.setItem(0, RepairFixtures.mixed(false));
        crafter.setItem(8, RepairFixtures.mixed(true));
        ((CrafterInvoker) Blocks.CRAFTER).leggings$dispense(level.getBlockState(pos), level, pos);
        require(!crafter.getItem(0).isEmpty() && !crafter.getItem(8).isEmpty() && barrel.isEmpty(), "Crafter accepted Mixed/Mixed repair");
        level.removeBlock(pos, false);
        level.removeBlock(output, false);
    }

    private static AbstractCraftingMenu menu(MinecraftServer server, ServerPlayer player, boolean table) {
        return table ? new CraftingMenu(72, player.getInventory(), ContainerLevelAccess.create(server.overworld(), new BlockPos(0, 65, 0)))
                : new InventoryMenu(player.getInventory(), true, player);
    }

    private static int expectedDamage(ItemStack root, ItemStack donor) {
        return Math.max(0, root.getDamageValue() - (donor.getMaxDamage() - donor.getDamageValue()) - root.getMaxDamage() * 5 / 100);
    }
}
