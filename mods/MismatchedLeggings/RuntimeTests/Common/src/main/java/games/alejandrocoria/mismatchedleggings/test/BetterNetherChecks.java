package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Optional profile only: reflection does not put Better Nether on ordinary fixture classpaths. */
final class BetterNetherChecks {
    private BetterNetherChecks() {
    }

    static void verify(MinecraftServer server) throws Exception {
        equippedChecks(RepairFixtures.player(server), server.registryAccess());
        BetterNetherRubyChecks.verify(server);
    }

    static void equippedChecks(Player player, HolderLookup.Provider registries) throws ReflectiveOperationException {
        var configs = Class.forName("org.betterx.betternether.config.Configs");
        var gameRules = configs.getField("GAME_RULES").get(null);
        var option = gameRules.getClass().getField("piglinIgnoreNetherArmor").get(gameRules);
        var get = option.getClass().getMethod("get");
        var set = option.getClass().getMethod("set", Object.class);
        var original = get.invoke(option);
        player.getInventory().clearContent();
        try {
            // Reuse the same roots across on/off/on changes, after warming both native and local caches.
            for (String tier : List.of("cincinnasite", "nether_ruby", "flaming_ruby")) {
                var source = nativeStack(registries, tier + "_leggings");
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
                for (boolean enabled : List.of(true, false, true)) {
                    set.invoke(option, enabled);
                    answer(player, source, enabled);
                    answer(player, pair.primary(), enabled);
                    answer(player, pair.inverse(), enabled);
                }
            }

            set.invoke(option, true);
            var source = nativeStack(registries, "nether_ruby_leggings");
            var pair = MixedLeggingsFactory.createPair(source, source);
            answer(player, pair.primary(), true);
            answer(player, pair.inverse(), true);
            var root = pair.primary();
            var bare = new ItemStack(root.getItem());
            var malformed = root.copy();
            malformed.remove(DataComponents.EQUIPPABLE);
            var suspended = root.copy();
            suspended.set(LeggingsContent.createSuspendedDataType(),
                    SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
            var unrelated = new ItemStack(Items.IRON_LEGGINGS);
            unrelated.set(LeggingsContent.mixedLeggingsDataType(), MixedLeggingsStructure.validData(root).orElseThrow());
            for (var invalid : List.of(bare, root.copyWithCount(2), malformed, suspended, unrelated)) {
                answer(player, invalid, false);
            }
            answer(player, new ItemStack(Items.IRON_LEGGINGS), false);
            clearArmor(player);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(!PiglinAi.isWearingSafeArmor(player), "Leggings in the wrong slot granted recognition");
            clearArmor(player);
            player.getInventory().add(root.copy());
            require(!PiglinAi.isWearingSafeArmor(player), "Inventory-only leggings granted recognition");
            player.getInventory().clearContent();

            set.invoke(option, false);
            answer(player, root, false);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            require(PiglinAi.isWearingSafeArmor(player), "Disabled option suppressed independent vanilla recognition");

            if (Boolean.getBoolean("leggings.test.boots")) {
                var boots = BootsCoexistence.pair(nativeStack(registries, "nether_ruby_boots"), new ItemStack(Items.IRON_BOOTS));
                for (boolean enabled : List.of(true, false, true)) {
                    set.invoke(option, enabled);
                    for (var bootRoot : boots) {
                        clearArmor(player);
                        player.setItemSlot(EquipmentSlot.FEET, bootRoot);
                        require(PiglinAi.isWearingSafeArmor(player) == enabled, "Boots alone lost native recognition");
                        player.setItemSlot(EquipmentSlot.LEGS, root);
                        require(PiglinAi.isWearingSafeArmor(player) == enabled, "Combined mixed pieces require an original armor anchor");
                        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        require(PiglinAi.isWearingSafeArmor(player) == enabled, "Leggings depended on the Boots fallback");
                    }
                }
            }
        } finally {
            set.invoke(option, original);
            clearArmor(player);
            player.getInventory().clearContent();
        }
    }

    static ItemStack nativeStack(HolderLookup.Provider registries, String name) throws ReflectiveOperationException {
        var id = Identifier.fromNamespaceAndPath("betternether", name);
        var item = BuiltInRegistries.ITEM.getValue(id);
        require(BuiltInRegistries.ITEM.getKey(item).equals(id), "Missing pinned native armor: " + id);
        var stack = item.getDefaultInstance();
        item.getClass().getMethod("setupItemStack", ItemStack.class, HolderLookup.Provider.class).invoke(item, stack, registries);
        return stack;
    }

    private static void answer(Player player, ItemStack root, boolean expected) {
        clearArmor(player);
        var before = root.copy();
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        var left = data == null ? ItemStack.EMPTY : data.left().source().copy();
        var right = data == null ? ItemStack.EMPTY : data.right().source().copy();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        require(PiglinAi.isWearingSafeArmor(player) == expected, "Wrong native piglin answer for " + root);
        require(ItemStack.matches(root, before), "Piglin query mutated its worn input");
        require(data == null || ItemStack.matches(left, data.left().source()) && ItemStack.matches(right, data.right().source()),
                "Piglin query mutated stored source snapshots");
    }

    private static void clearArmor(Player player) {
        for (var slot : EquipmentSlotGroup.ARMOR) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}
