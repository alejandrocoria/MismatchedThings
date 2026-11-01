package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class AnvilRepairChecks {
    private AnvilRepairChecks() {
    }

    static void verify(MinecraftServer server) {
        for (boolean inverse : List.of(false, true)) {
            names(server, inverse);
            for (var material : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
                materials(server, inverse, new ItemStack(material, 8));
            }
            for (var item : List.of(Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS)) {
                var root = RepairFixtures.mixed(inverse);
                var donor = new ItemStack(item);
                donor.setDamageValue(donor.getMaxDamage() - 10);
                donor.set(DataComponents.REPAIR_COST, 2);
                var unbreaking = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
                donor.enchant(unbreaking, 1);
                var player = RepairFixtures.player(server);
                var menu = ready(player, root, donor);
                var result = menu.getSlot(2).getItem();
                RepairFixtures.preserved(root, result);
                int damage = Math.max(0, root.getMaxDamage() - 20 - 10 - root.getMaxDamage() * 12 / 100);
                require(result.getDamageValue() == damage && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, result) == 1
                                && result.getOrDefault(DataComponents.REPAIR_COST, 0) == 7,
                        "Anvil donor damage, enchantment or prior-work rules changed");
                paid(player, menu, true, 0);
            }
        }
        books(server);
        invalid(server);
        materialTagRebind(server);
        controls(server);
    }

    private static void names(MinecraftServer server, boolean inverse) {
        var player = RepairFixtures.player(server);
        var root = RepairFixtures.mixed(inverse);
        var menu = ready(player, root, ItemStack.EMPTY);
        menu.setItemName("Renamed live root");
        var result = menu.getSlot(2).getItem();
        RepairFixtures.preserved(root, result);
        require(result.getHoverName().getString().equals("Renamed live root") && menu.getCost() == 4
                        && result.get(DataComponents.REPAIR_COST).equals(3), "Rename changed native cost/prior work");
        paid(player, menu, false, 0);

        var removal = ready(player, root.copy(), ItemStack.EMPTY);
        removal.setItemName("");
        RepairFixtures.preserved(root, removal.getSlot(2).getItem());
        require(!removal.getSlot(2).getItem().has(DataComponents.CUSTOM_NAME), "Anvil name removal failed");
        paid(player, removal, false, 0);

        var expensive = root.copy();
        expensive.set(DataComponents.REPAIR_COST, 100);
        var rename = ready(player, expensive, ItemStack.EMPTY);
        rename.setItemName("Expensive rename");
        require(rename.getCost() == 39 && rename.getSlot(2).hasItem(), "Native rename-only cost cap changed");

        var cancelled = ready(player, root.copy(), new ItemStack(Items.IRON_INGOT, 8));
        int before = player.experienceLevel;
        var source = cancelled.getSlot(0).getItem().copy();
        var addition = cancelled.getSlot(1).getItem().copy();
        player.getInventory().clearContent();
        cancelled.removed(player);
        require(player.experienceLevel == before && WorkbenchChecks.inventory(player).size() == 2
                        && WorkbenchChecks.contains(player, source) && WorkbenchChecks.contains(player, addition),
                "Closing an unpaid anvil lost inputs or spent XP");
    }

    private static void materials(MinecraftServer server, boolean inverse, ItemStack addition) {
        var root = RepairFixtures.mixed(inverse);
        var player = RepairFixtures.player(server);
        var menu = ready(player, root, addition);
        var result = menu.getSlot(2).getItem();
        RepairFixtures.preserved(root, result);
        int used = (root.getDamageValue() + root.getMaxDamage() / 4 - 1) / (root.getMaxDamage() / 4);
        require(result.getDamageValue() == 0 && menu.getCost() == 3 + used,
                "Material repair did not use native quarter-durability/count/cost rules");
        paid(player, menu, false, 8 - used);
    }

    private static void books(MinecraftServer server) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var fire = enchantments.getOrThrow(Enchantments.FIRE_PROTECTION);
        var feather = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
        var player = RepairFixtures.player(server);
        var root = RepairFixtures.mixed(false);
        root.enchant(protection, 3);
        var menu = ready(player, root, book(protection, 3));
        RepairFixtures.preserved(root, menu.getSlot(2).getItem());
        require(EnchantmentHelper.getItemEnchantmentLevel(protection, menu.getSlot(2).getItem()) == 4,
                "Future book did not use native duplicate-level rules");
        paid(player, menu, false, 0);
        root.enchant(protection, 4);
        var capped = ready(player, root, book(protection, 4));
        require(EnchantmentHelper.getItemEnchantmentLevel(protection, capped.getSlot(2).getItem()) == 4,
                "Anvil repeated composition-time overlevel merging");
        rejected(player, root, book(fire, 1));
        rejected(player, root, book(feather, 1));
        var expensive = root.copy();
        expensive.set(DataComponents.REPAIR_COST, 40);
        rejected(player, expensive, new ItemStack(Items.IRON_INGOT));
        var unpaid = ready(player, root, book(protection, 4));
        player.giveExperienceLevels(-player.experienceLevel);
        require(unpaid.getSlot(2).safeTake(1, 1, player).isEmpty() && unpaid.getSlot(0).hasItem()
                        && unpaid.getSlot(1).hasItem(), "Anvil paid an unaffordable survival result");
    }

    private static void invalid(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var root = RepairFixtures.mixed(false);
        rejected(player, root, new ItemStack(Items.DIAMOND_LEGGINGS));
        rejected(player, root, new ItemStack(Items.DIAMOND));
        rejected(player, root, RepairFixtures.mixed(true));
        rejected(player, new ItemStack(Items.IRON_LEGGINGS), root);
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        bare.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        rejected(player, bare, new ItemStack(Items.IRON_INGOT));
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        rejected(player, malformed, new ItemStack(Items.IRON_INGOT));
        rejected(player, malformed, new ItemStack(Items.IRON_LEGGINGS));
        rejected(player, root.copyWithCount(2), new ItemStack(Items.IRON_INGOT));
    }

    private static void materialTagRebind(MinecraftServer server) {
        var original = new LinkedHashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> original.put(tag.key(), tag.stream().toList()));
        var existing = RepairFixtures.mixed(false);
        try {
            // Apply both named sets and holder memberships, as native tag reload does.
            var changed = new LinkedHashMap<>(original);
            changed.put(ItemTags.REPAIRS_IRON_ARMOR, List.of(new ItemStack(Items.EMERALD).typeHolder()));
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
            require(new ItemStack(Items.IRON_LEGGINGS).isValidRepairItem(new ItemStack(Items.EMERALD)),
                    "Native tag-reload control did not change");
            var fresh = RepairFixtures.mixed(false);
            require(fresh.isValidRepairItem(new ItemStack(Items.EMERALD)) && !fresh.isValidRepairItem(new ItemStack(Items.IRON_INGOT))
                            && existing.isValidRepairItem(new ItemStack(Items.IRON_INGOT))
                            && !existing.isValidRepairItem(new ItemStack(Items.EMERALD)),
                    "Repair materials lost the existing-root snapshot/new-composition boundary");
            var player = RepairFixtures.player(server);
            var menu = ready(player, fresh, new ItemStack(Items.EMERALD, 8));
            require(menu.getSlot(2).hasItem(), "Newly composed repair material did not reach the native anvil");
        } finally {
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, original)).apply();
        }
    }

    private static void controls(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var ordinary = new ItemStack(Items.IRON_LEGGINGS);
        ordinary.setDamageValue(50);
        var menu = ready(player, ordinary, new ItemStack(Items.IRON_INGOT));
        require(menu.getSlot(2).hasItem() && menu.getSlot(2).getItem().is(Items.IRON_LEGGINGS), "Native material repair was suppressed");
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.mixed();
            boots.setDamageValue(boots.getMaxDamage() - 10);
            var donor = new ItemStack(Items.GOLDEN_BOOTS);
            donor.setDamageValue(donor.getMaxDamage() - 4);
            var control = ready(player, boots, donor);
            require(control.getSlot(2).hasItem(), "Leggings suppressed Boots' source donor hook");
            paid(player, control, true, 0);
            rejected(player, RepairFixtures.mixed(false), boots);
        }
    }

    static AnvilMenu ready(ServerPlayer player, ItemStack root, ItemStack addition) {
        var before = root.copy();
        var donor = addition.copy();
        var menu = new AnvilMenu(71, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
        menu.getSlot(0).set(root);
        root.setCount(before.getCount());
        menu.getSlot(1).set(addition);
        menu.setItemName(root.getHoverName().getString());
        require(ItemStack.matches(root, before) && ItemStack.matches(addition, donor), "Anvil preview mutated input state");
        return menu;
    }

    private static void rejected(ServerPlayer player, ItemStack root, ItemStack addition) {
        var menu = ready(player, root.copy(), addition.copy());
        require(!menu.getSlot(2).hasItem() && menu.quickMoveStack(player, 2).isEmpty(), "Anvil accepted an invalid combination");
    }

    private static void paid(ServerPlayer player, AnvilMenu menu, boolean shift, int remainingAddition) {
        int before = player.experienceLevel;
        int cost = menu.getCost();
        var preview = menu.getSlot(2).getItem().copy();
        var taken = shift ? menu.quickMoveStack(player, 2) : menu.getSlot(2).safeTake(1, 1, player);
        require(!taken.isEmpty() && ItemStack.matches(preview, taken) && cost > 0
                        && player.experienceLevel == before - cost && !menu.getSlot(0).hasItem()
                        && menu.getSlot(1).getItem().getCount() == remainingAddition,
                "Anvil payment, output or material consumption differs from preview");
        require(menu.getSlot(2).safeTake(1, 1, player).isEmpty() && player.experienceLevel == before - cost,
                "Anvil allowed a duplicate payment/output");
    }

    static ItemStack book(Holder<Enchantment> enchantment, int level) {
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, level);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }
}
