package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Pinned native admission controls and ordinary paid menus; no Roots ritual adapter. */
public final class RootsWorkstationChecks {
    private RootsWorkstationChecks() {
    }

    public static void verify(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        for (String path : List.of("sylvan_tunic", "wildwood_leggings")) {
            var nativeStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("rootsclassic:" + path)));
            require(!nativeStack.isCombineRepairable(), "Pinned Roots no-combine-repair control changed");
            var nativeInput = CraftingInput.of(2, 1, List.of(nativeStack.copy(), nativeStack.copy()));
            require(!RepairItemRecipe.INSTANCE.matches(nativeInput, server.overworld()), "Native crafting ignored its repair veto");
            var pair = MixedLeggingsFactory.createPair(nativeStack, new ItemStack(Items.IRON_LEGGINGS));
            for (var input : List.of(nativeStack, pair.primary(), pair.inverse())) {
                var root = input.copy();
                root.setDamageValue(root.getMaxDamage() / 2);
                var donor = nativeStack.copy();
                var before = root.copy();
                var crafting = CraftingInput.of(2, 1, List.of(root.copy(), donor.copy()));
                require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, server.overworld()).isEmpty(),
                        "Roots donor bypassed its native crafting veto");
                var grindstone = GrindstoneChecks.ready(player, root.copy(), donor.copy());
                require(grindstone.getSlot(2).getItem().isEmpty(), "Roots donor bypassed its native grindstone veto");

                var anvil = AnvilRepairChecks.ready(player, root.copy(), donor.copy());
                var preview = anvil.getSlot(2).getItem().copy();
                int levels = player.experienceLevel;
                int cost = anvil.getCost();
                require(!preview.isEmpty() && preview.getDamageValue() < root.getDamageValue() && cost > 0,
                        "Ordinary native/mixed anvil donor repair failed");
                var repaired = anvil.getSlot(2).safeTake(1, 1, player);
                require(ItemStack.matches(preview, repaired) && player.experienceLevel == levels - cost
                                && !anvil.getSlot(0).hasItem() && !anvil.getSlot(1).hasItem(),
                        "Anvil donor repair did not consume inputs/payment");

                var smithing = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
                smithing.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
                smithing.getSlot(1).set(root.copy());
                smithing.getSlot(2).set(new ItemStack(Items.DIAMOND, 2));
                smithing.createResult();
                var trimmed = smithing.getSlot(3).safeTake(1, 1, player);
                require(!trimmed.isEmpty() && smithing.getSlot(0).getItem().getCount() == 1
                                && smithing.getSlot(2).getItem().getCount() == 1 && !smithing.getSlot(1).hasItem(),
                        "Native/mixed trim admission or payment failed");
                var data = trimmed.get(LeggingsContent.mixedLeggingsDataType());
                require(data == null ? trimmed.has(DataComponents.TRIM)
                                : data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM),
                        "Trim did not reach the native item or both mixed sources");
                require(ItemStack.matches(before, root), "Workstation previews mutated the caller's stack");
            }
        }
    }
}
