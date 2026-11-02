package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class VanillaLeggingsChecks {
    private VanillaLeggingsChecks() {
    }

    static void verify(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "leggings-semantics"),
                ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(100);
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var swift = enchantments.getOrThrow(Enchantments.SWIFT_SNEAK);
        var feather = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var fireProtection = enchantments.getOrThrow(Enchantments.FIRE_PROTECTION);
        var mending = enchantments.getOrThrow(Enchantments.MENDING);
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        iron.enchant(swift, 3);
        var pair = pair(iron, new ItemStack(Items.GOLDEN_LEGGINGS));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(!root.is(ItemTags.LEG_ARMOR) && !root.is(ItemTags.TRIMMABLE_ARMOR)
                    && root.is(ItemTags.LEG_ARMOR_ENCHANTABLE)
                    && root.is(ItemTags.ARMOR_ENCHANTABLE) && root.is(ItemTags.DURABILITY_ENCHANTABLE)
                    && root.is(ItemTags.EQUIPPABLE_ENCHANTABLE), "Incorrect narrow leggings classification");
            require(!root.is(ItemTags.FOOT_ARMOR) && !root.is(ItemTags.FOOT_ARMOR_ENCHANTABLE)
                    && !root.is(ItemTags.CHEST_ARMOR) && !root.is(ItemTags.HEAD_ARMOR), "Cross-slot tag leakage");
            require(swift.value().canEnchant(root) && !feather.value().canEnchant(root), "Native leggings enchantment tags are wrong");
            var provenance = MixedLeggingsStructure.validData(root).orElseThrow();
            equip(player, EquipmentSlot.LEGS, root);
            require(Math.abs(player.getAttributeValue(Attributes.SNEAKING_SPEED) - 0.75) < 0.00001,
                    "Swift Sneak did not use the actual legs slot");
            require(PiglinAi.isWearingSafeArmor(player) && player.canFreeze(), "Gold behavior leaked or disappeared");
            require(new ItemStack(Items.GOLDEN_LEGGINGS).is(ItemTags.PIGLIN_LOVED) && !root.is(ItemTags.PIGLIN_LOVED),
                    "Whitelist expanded from gold's protection to its unrelated barter/admiration tag");
            require(provenance.equals(MixedLeggingsStructure.validData(root).orElseThrow()), "Equipment effects mutated sources");
            var clean = root.copy();
            clean.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            var nativeLeggings = new ItemStack(Items.IRON_LEGGINGS);
            enchantments.listElements().forEach(holder -> {
                var enchantment = holder.value();
                require(enchantment.canEnchant(clean) == enchantment.canEnchant(nativeLeggings)
                                && enchantment.isPrimaryItem(clean) == enchantment.isPrimaryItem(nativeLeggings),
                        "Narrow classification changed native enchantment eligibility: " + holder.key().identifier());
            });
            require(clean.isEnchantable(), "Composed enchantability did not reach the real root");
            var pool = enchantments.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE);
            var choices = EnchantmentHelper.getAvailableEnchantmentResults(25, clean, pool.stream());
            var nativeChoices = EnchantmentHelper.getAvailableEnchantmentResults(25, new ItemStack(Items.IRON_LEGGINGS), pool.stream());
            require(choices.equals(nativeChoices) && !choices.isEmpty(), "Table candidates diverged from native leggings");
            require(choices.stream().noneMatch(choice -> choice.enchantment().equals(swift)
                    || choice.enchantment().equals(feather) || choice.enchantment().equals(mending)),
                    "Treasure or foot-only enchantments leaked into the table pool");
            anvil(player, clean, swift, true);
            anvil(player, clean, feather, false);
            var conflicting = clean.copy();
            conflicting.enchant(protection, 4);
            anvil(player, conflicting, fireProtection, false);
        }

        equip(player, EquipmentSlot.LEGS, ItemStack.EMPTY);
        require(Math.abs(player.getAttributeValue(Attributes.SNEAKING_SPEED) - 0.3) < 0.00001,
                "Swift Sneak survived unequipping");
        var wrongSlot = new ItemStack(Items.IRON_BOOTS);
        wrongSlot.enchant(swift, 3);
        equip(player, EquipmentSlot.FEET, wrongSlot);
        require(Math.abs(player.getAttributeValue(Attributes.SNEAKING_SPEED) - 0.3) < 0.00001,
                "Swift Sneak control no longer checks equipment slot");
        equip(player, EquipmentSlot.FEET, ItemStack.EMPTY);
        require(!PiglinAi.isWearingSafeArmor(player) && player.canFreeze(), "Unequipped vanilla control failed");

        var leatherPair = pair(new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS));
        for (var root : List.of(leatherPair.primary(), leatherPair.inverse())) {
            equip(player, EquipmentSlot.LEGS, root);
            require(!player.canFreeze() && !PiglinAi.isWearingSafeArmor(player), "Leather freeze behavior did not project");
        }
        var none = pair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)).primary();
        equip(player, EquipmentSlot.LEGS, none);
        require(!PiglinAi.isWearingSafeArmor(player) && player.canFreeze(), "No-source behavior became active");
        require(!none.is(ItemTags.PIGLIN_SAFE_ARMOR) && !none.is(ItemTags.FREEZE_IMMUNE_WEARABLES), "Unrelated pair projected a behavior");
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        require(!bare.isEnchantable() && !bare.is(ItemTags.PIGLIN_SAFE_ARMOR)
                && !bare.is(ItemTags.FREEZE_IMMUNE_WEARABLES), "Bare roots inherited behavior");
        // Exercise admission rather than rejecting only because native enchantment storage is absent.
        bare.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        anvil(player, bare, swift, false);
        var malformed = pair.primary().copyWithCount(2);
        require(!malformed.is(ItemTags.PIGLIN_SAFE_ARMOR), "Malformed roots inherited gold behavior");
        anvil(player, malformed, swift, false);
        var foreign = new ItemStack(Items.STICK);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), pair.primary().get(LeggingsContent.mixedLeggingsDataType()));
        require(!foreign.is(ItemTags.PIGLIN_SAFE_ARMOR), "Foreign pair component activated projection");
        require(new ItemStack(Items.GOLDEN_LEGGINGS).is(ItemTags.PIGLIN_SAFE_ARMOR), "Native positive tag changed");
        anvil(player, new ItemStack(Items.IRON_LEGGINGS), swift, true);
        anvil(player, new ItemStack(Items.IRON_LEGGINGS), feather, false);
        // Source rejection must win over the real root's new armor tags.
        var odd = pair(legs(new ItemStack(Items.PAPER)), legs(new ItemStack(Items.STICK)));
        require(protection.value().canEnchant(odd.primary()), "Source-rejecting control no longer has native root admission");
        anvil(player, odd.primary(), protection, false);
        anvil(player, odd.inverse(), protection, false);

        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.mixed();
            require(boots.is(ItemTags.PIGLIN_SAFE_ARMOR) && !boots.is(ItemTags.LEG_ARMOR), "Boots tag guard was suppressed");
            anvil(player, boots, feather, true);
            anvil(player, boots, swift, false);
        }
    }

    private static games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsPair pair(ItemStack first, ItemStack second) {
        return MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults());
    }

    private static ItemStack legs(ItemStack source) {
        source.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_LEGGINGS).get(DataComponents.EQUIPPABLE));
        return source;
    }

    private static void equip(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        player.setItemSlot(slot, stack);
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
    }

    private static void anvil(ServerPlayer player, ItemStack source, Holder<Enchantment> enchantment, boolean accepted) {
        var input = source.copy();
        var original = input.copy();
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        var menu = new AnvilMenu(1, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
        // Native slot insertion clamps over-stacked input; preserve the malformed-count
        // negative after that insertion, before the book triggers result computation.
        input.setCount(source.getCount());
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        require(result.isEmpty() != accepted, "Survival anvil admission: " + enchantment.getRegisteredName());
        require(ItemStack.matches(input, original) && book.getCount() == 1, "Preview mutated input or paid for a book");
        if (accepted) {
            require(EnchantmentHelper.getItemEnchantmentLevel(enchantment, result) > 0, "Anvil result lacks its accepted enchantment");
        }
    }
}
