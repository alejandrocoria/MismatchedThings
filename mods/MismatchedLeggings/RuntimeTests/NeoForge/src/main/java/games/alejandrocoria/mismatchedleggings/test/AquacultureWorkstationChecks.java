package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native acceptance controls and paid vanilla menus, not synthetic recipe results. */
final class AquacultureWorkstationChecks {
    private AquacultureWorkstationChecks() {
    }

    static void verify(MinecraftServer server) {
        var source = AquacultureChecks.nativeItem("neptunium_leggings");
        var inputs = new ArrayList<ItemStack>();
        inputs.add(source);
        inputs.addAll(AquacultureChecks.pair(source, new ItemStack(Items.IRON_LEGGINGS)));
        for (var input : inputs) {
            var player = RepairFixtures.player(server);
            var damaged = input.copy();
            damaged.setDamageValue(damaged.getMaxDamage() / 2);
            var repaired = anvil(player, damaged, AquacultureChecks.nativeItem("neptunium_ingot").copyWithCount(8));
            require(repaired.getDamageValue() < damaged.getDamageValue(), "Paid Neptunium repair did not restore durability");
            require(input.getItem() == repaired.getItem(), "Repair changed item identity");
            if (MixedLeggingsStructure.validData(input).isPresent()) {
                require(input.get(LeggingsContent.mixedLeggingsDataType()).equals(repaired.get(LeggingsContent.mixedLeggingsDataType())),
                        "Anvil repair rewrote source history");
            }

            var enchanted = input.copy();
            for (var key : List.of(Enchantments.MENDING, Enchantments.UNBREAKING, Enchantments.SWIFT_SNEAK)) {
                var enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
                enchanted = anvil(player, enchanted, AnvilRepairChecks.book(enchantment, 1));
                require(EnchantmentHelper.getItemEnchantmentLevel(enchantment, enchanted) == 1,
                        "Paid native enchantment book was rejected: " + key);
            }
            trim(player, enchanted);
        }
    }

    private static ItemStack anvil(ServerPlayer player, ItemStack input, ItemStack addition) {
        var before = input.copy();
        var menu = AnvilRepairChecks.ready(player, input.copy(), addition.copy());
        int levels = player.experienceLevel;
        int cost = menu.getCost();
        var preview = menu.getSlot(2).getItem().copy();
        require(!preview.isEmpty() && cost > 0, "Native anvil produced no payable preview");
        var result = menu.getSlot(2).safeTake(1, 1, player);
        require(ItemStack.matches(result, preview) && !menu.getSlot(0).hasItem()
                        && menu.getSlot(1).getItem().getCount() < addition.getCount()
                        && player.experienceLevel == levels - cost && menu.getSlot(2).safeTake(1, 1, player).isEmpty(),
                "Anvil output/payment or repeat-take boundary failed");
        require(ItemStack.matches(input, before), "Anvil mutated the caller's input");
        return result;
    }

    private static void trim(ServerPlayer player, ItemStack input) {
        var before = input.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
        menu.getSlot(1).set(input.copy());
        menu.getSlot(2).set(new ItemStack(Items.DIAMOND, 2));
        menu.createResult();
        var preview = menu.getSlot(3).getItem().copy();
        require(!preview.isEmpty(), "Native trim acceptance control failed");
        var result = menu.getSlot(3).safeTake(1, 1, player);
        require(ItemStack.matches(preview, result) && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1
                        && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Trim payment/repeated take failed");
        require(ItemStack.matches(before, input) && result.get(DataComponents.ENCHANTMENTS).equals(input.get(DataComponents.ENCHANTMENTS)),
                "Trimming mutated input or lost live enchantments");
        var data = MixedLeggingsStructure.validData(result).orElse(null);
        require(data == null ? result.has(DataComponents.TRIM)
                        : data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM),
                "Trim result did not retain the native/source trim");
    }
}
