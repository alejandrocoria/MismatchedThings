package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native recipe selection, real menu payment and stale-preview rejection, not helper-only assembly. */
final class ForbiddenModifierSmithingChecks {
    private ForbiddenModifierSmithingChecks() {}

    static void server(MinecraftServer server) throws Exception {
        orders(server);
        liveAdmission(server);
        reloads(server);
        workstations(server);
        nativeControls(server);
        Files.writeString(ForbiddenArcanusChecks.report("modifiers-smithing"),
                "Both orientations/orders, named/unnamed roots, normal/shift extraction and exact native recipe credit passed;\n"
                + "duplicates, wrong materials, bare/malformed roots, live enchantments, item/tag and recipe rebinds passed;\n"
                + "immutable sources/sibling, finite Soulbound wear state, trim, anvil, grindstone and crafting passed;\n"
                + "native originals retain their own modifier path; final presentation remains outside this report\n");
    }

    private static void orders(MinecraftServer server) {
        for (boolean inverse : List.of(false, true)) {
            for (boolean shift : List.of(false, true)) {
                for (String first : List.of("eternal", "soulbound")) {
                    var pair = MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS),
                            CompositionRules.defaults(), server.registryAccess());
                    var root = decorate(server, inverse ? pair.inverse() : pair.primary(), shift);
                    var sibling = inverse ? pair.primary() : pair.inverse();
                    var siblingBefore = sibling.copy();
                    var history = MixedLeggingsStructure.validData(root).orElseThrow();
                    var player = ForbiddenModifierChecks.actor(server);
                    try {
                        var once = upgrade(player, root, first, shift);
                        preserved(root, once, first.equals("eternal"));
                        rejected(player, once, template(), addition(first));
                        String second = first.equals("eternal") ? "soulbound" : "eternal";
                        var both = upgrade(player, once, second, !shift);
                        preserved(once, both, true);
                        ForbiddenModifierChecks.verify(both, new ForbiddenModifiers(true, true));
                        require(history.equals(MixedLeggingsStructure.validData(both).orElseThrow())
                                        && ItemStack.matches(siblingBefore, sibling), "Root upgrades rewrote history or its sibling");
                        rejected(player, both, template(), addition("eternal"));
                        rejected(player, both, template(), addition("soulbound"));
                        ForbiddenModifierChecks.codecs(server, both);
                    } finally {
                        ForbiddenModifierChecks.close(player);
                    }
                }
            }
        }
    }

    private static void liveAdmission(MinecraftServer server) {
        var player = ForbiddenModifierChecks.actor(server);
        try {
            var plain = plain(server);
            rejected(player, plain, template(), new ItemStack(Items.DIAMOND));
            rejected(player, plain, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), addition("eternal"));
            rejected(player, new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID)), template(), addition("eternal"));
            var malformed = plain.copy();
            malformed.remove(DataComponents.EQUIPPABLE);
            rejected(player, malformed, template(), addition("soulbound"));

            var unbreaking = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            var blocked = plain.copy();
            blocked.enchant(unbreaking, 3);
            rejected(player, blocked, template(), addition("eternal"));
            var unknown = plain.copy();
            copyComponent(unknown, ForbiddenModifierChecks.source(server, "fiery", true),
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("forbidden_arcanus:modifier")));
            rejected(player, unknown, template(), addition("eternal"));
            rejected(player, unknown, template(), addition("soulbound"));
            var historical = new ItemStack(Items.DIAMOND_LEGGINGS);
            historical.enchant(unbreaking, 3);
            var cleared = MixedLeggingsFactory.createPair(historical, new ItemStack(Items.IRON_LEGGINGS),
                    CompositionRules.defaults(), server.registryAccess()).inverse();
            cleared.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            ForbiddenModifierChecks.verify(upgrade(player, cleared, "eternal", false), new ForbiddenModifiers(true, false));

            // A deliberately different live state proves the scalar markers in history do not
            // decide which paid upgrade is missing. No production deactivation/migration is added.
            var fromHistory = MixedLeggingsFactory.createPair(ForbiddenModifierChecks.source(server, "soulbound", true),
                    new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults(), server.registryAccess()).inverse();
            fromHistory.remove(LeggingsContent.forbiddenModifiersType());
            fromHistory.remove(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("forbidden_arcanus:modifier")));
            var history = MixedLeggingsStructure.validData(fromHistory).orElseThrow();
            var installed = upgrade(player, fromHistory, "soulbound", true);
            require(history.equals(MixedLeggingsStructure.validData(installed).orElseThrow()), "Inactive source marker blocked or rewrote a live install");
        } finally {
            ForbiddenModifierChecks.close(player);
        }
    }

    private static void reloads(MinecraftServer server) {
        var recipes = List.copyOf(server.getRecipeManager().getRecipes());
        var eternalId = Identifier.parse("forbidden_arcanus:apply_modifier/eternal");
        var original = recipes.stream().filter(recipe -> recipe.id().identifier().equals(eternalId)).findFirst().orElseThrow();
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (boolean shift : List.of(false, true)) {
                var stale = ready(player, plain(server), "eternal");
                WorkbenchChecks.applyRecipes(server, recipes.stream().filter(recipe -> recipe != original).toList());
                staleRejected(player, stale, shift);
                WorkbenchChecks.applyRecipes(server, recipes);
                var replaced = ready(player, plain(server), "eternal");
                var replacedRecipes = new ArrayList<RecipeHolder<?>>(recipes);
                replacedRecipes.remove(original);
                replacedRecipes.add(replacementHolder(original));
                WorkbenchChecks.applyRecipes(server, replacedRecipes);
                staleRejected(player, replaced, shift);
                WorkbenchChecks.applyRecipes(server, recipes);
            }

            var tags = new LinkedHashMap<>(BuiltInRegistries.ITEM.getTags().collect(java.util.stream.Collectors.toMap(
                    net.minecraft.core.HolderSet.Named::key, tag -> tag.stream().toList())));
            try {
                var pending = ready(player, plain(server), "eternal");
                var changed = new LinkedHashMap<>(tags);
                var forbidden = TagKey.<Item>create(Registries.ITEM, Identifier.parse("forbidden_arcanus:modifier/eternal_incompatible"));
                changed.put(forbidden, List.of(Items.DIAMOND_LEGGINGS.builtInRegistryHolder(), Items.IRON_LEGGINGS.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                rejected(player, plain(server), template(), addition("eternal"));
                staleRejected(player, pending, false);
                changed.put(forbidden, List.of(BuiltInRegistries.ITEM.get(LeggingsContent.MIXED_LEGGINGS_ID).orElseThrow()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                rejected(player, plain(server), template(), addition("eternal"));
                // Restore only one native identity: either orientation must still find the eligible side.
                changed.put(forbidden, List.of(Items.DIAMOND_LEGGINGS.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                ForbiddenModifierChecks.verify(upgrade(player, plain(server), "eternal", true), new ForbiddenModifiers(true, false));
            } finally {
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
            }
        } finally {
            WorkbenchChecks.applyRecipes(server, recipes);
            ForbiddenModifierChecks.close(player);
        }
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> replacementHolder(RecipeHolder<T> original) {
        return new RecipeHolder<>(original.id(), original.value());
    }

    private static void workstations(MinecraftServer server) {
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (boolean eternal : List.of(false, true)) {
                var root = upgrade(player, plain(server), "soulbound", false);
                if (eternal) {
                    root = upgrade(player, root, "eternal", true);
                }
                var flags = root.get(LeggingsContent.forbiddenModifiersType());
                var trim = SmithingChecks.ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), root.copy(), new ItemStack(Items.DIAMOND));
                var trimmed = take(player, trim, false);
                ForbiddenModifierChecks.verify(trimmed, flags);
                var data = MixedLeggingsStructure.validData(trimmed).orElseThrow();
                require(data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM), "Trim did not update both appearances");

                var anvil = AnvilRepairChecks.ready(player, trimmed.copy(), ItemStack.EMPTY);
                anvil.setItemName("Renamed modifier root");
                var renamed = anvil.getSlot(2).safeTake(1, 1, player);
                ForbiddenModifierChecks.verify(renamed, flags);
                require(renamed.getHoverName().getString().equals("Renamed modifier root"), "Anvil failed to rename modified root");
                var grindstone = GrindstoneChecks.ready(player, renamed.copy(), ItemStack.EMPTY);
                var ground = grindstone.getSlot(2).safeTake(1, 1, player);
                ForbiddenModifierChecks.verify(ground, flags);
                require(ground.getEnchantments().isEmpty(), "Grindstone did not remove native enchantments");
                ForbiddenModifierChecks.codecs(server, ground);
            }
            var finite = upgrade(player, plain(server), "soulbound", false);
            player.getInventory().clearContent();
            var crafting = new InventoryMenu(player.getInventory(), true, player);
            crafting.getInputGridSlots().get(0).set(finite.copy());
            var donor = new ItemStack(Items.DIAMOND_LEGGINGS);
            donor.setDamageValue(donor.getMaxDamage() - 10);
            crafting.getInputGridSlots().get(1).set(donor);
            var repaired = crafting.getSlot(0).safeTake(1, 1, player);
            ForbiddenModifierChecks.verify(repaired, new ForbiddenModifiers(false, true));
            require(repaired.getDamageValue() < finite.getDamageValue(), "Crafting failed to repair finite Soulbound root");
            crafting.getInputGridSlots().forEach(slot -> require(!slot.hasItem(), "Crafting did not consume both inputs"));
        } finally {
            ForbiddenModifierChecks.close(player);
        }
    }

    private static void nativeControls(MinecraftServer server) {
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (String kind : List.of("eternal", "soulbound")) {
                var nativeRoot = new ItemStack(Items.DIAMOND_LEGGINGS);
                var menu = SmithingChecks.ready(player, template(), nativeRoot, addition(kind));
                var result = take(player, menu, false);
                require(result.is(Items.DIAMOND_LEGGINGS) && !result.has(LeggingsContent.forbiddenModifiersType())
                                && ForbiddenModifierChecks.nativeKind(result).equals(kind), "Native original no longer follows its own modifier path");
                rejected(player, result, template(), addition(kind));
            }
        } finally {
            ForbiddenModifierChecks.close(player);
        }
    }

    private static ItemStack plain(MinecraftServer server) {
        return decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS),
                CompositionRules.defaults(), server.registryAccess()).primary(), true);
    }

    private static ItemStack decorate(MinecraftServer server, ItemStack root, boolean named) {
        root.setDamageValue(37);
        root.set(DataComponents.REPAIR_COST, 3);
        root.set(CompositionFixture.CHARGE, 123L);
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
        if (named) {
            root.set(DataComponents.CUSTOM_NAME, Component.literal("Live modifier root"));
        }
        return root;
    }

    static ItemStack upgrade(ServerPlayer player, ItemStack root, String kind, boolean shift) {
        var before = root.copy();
        var menu = ready(player, root, kind);
        var output = take(player, menu, shift);
        require(ItemStack.matches(before, root), "Paid root upgrade mutated input object");
        return output;
    }

    private static SmithingMenu ready(ServerPlayer player, ItemStack root, String kind) {
        player.getInventory().clearContent();
        return SmithingChecks.ready(player, template().copyWithCount(2), root.copy(), addition(kind).copyWithCount(2));
    }

    private static ItemStack take(ServerPlayer player, SmithingMenu menu, boolean shift) {
        var output = menu.getSlot(3).getItem().copy();
        require(!output.isEmpty(), "Missing payable modifier/workstation result");
        var recipe = ((ResultContainer) menu.getSlot(3).container).getRecipeUsed();
        int credits = SmithingCredits.count(player, recipe.id().identifier());
        int templates = menu.getSlot(0).getItem().getCount();
        int additions = menu.getSlot(2).getItem().getCount();
        var taken = shift ? menu.quickMoveStack(player, 3) : menu.getSlot(3).safeTake(1, 1, player);
        require(ItemStack.matches(output, taken) && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == Math.max(0, templates - 1)
                        && menu.getSlot(2).getItem().getCount() == Math.max(0, additions - 1), "Smithing preview/payment changed at extraction");
        require(SmithingCredits.count(player, recipe.id().identifier()) == credits + 1, "Native recipe was not credited exactly once");
        require(menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Repeated take produced an extra item");
        return taken;
    }

    private static void preserved(ItemStack before, ItemStack after, boolean eternal) {
        var expected = before.copy();
        expected.set(LeggingsContent.forbiddenModifiersType(), after.get(LeggingsContent.forbiddenModifiersType()));
        copyComponent(expected, after, BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("forbidden_arcanus:modifier")));
        if (eternal) {
            expected.remove(DataComponents.MAX_DAMAGE);
            expected.remove(DataComponents.DAMAGE);
        }
        require(ItemStack.matches(expected, after), "Root upgrade changed history, attributes, damage, enchantments, names or live state");
    }

    private static <T> void copyComponent(ItemStack target, ItemStack source, DataComponentType<T> type) {
        target.set(type, source.get(type));
    }

    private static void rejected(ServerPlayer player, ItemStack root, ItemStack template, ItemStack addition) {
        var before = root.copy();
        var menu = SmithingChecks.ready(player, template.copy(), root.copy(), addition.copy());
        require(!menu.getSlot(3).hasItem(), "Invalid/duplicate upgrade exposed a result");
        staleRejected(player, menu, false);
        staleRejected(player, menu, true);
        require(ItemStack.matches(before, menu.getSlot(1).getItem()), "Rejected upgrade mutated the root");
    }

    private static void staleRejected(ServerPlayer player, SmithingMenu menu, boolean shift) {
        var inputs = List.of(menu.getSlot(0).getItem().copy(), menu.getSlot(1).getItem().copy(), menu.getSlot(2).getItem().copy());
        var taken = shift ? menu.quickMoveStack(player, 3) : menu.getSlot(3).safeTake(1, 1, player);
        require(taken.isEmpty(), "Stale or rejected result was extracted");
        for (int i = 0; i < 3; i++) {
            require(ItemStack.matches(inputs.get(i), menu.getSlot(i).getItem()), "Refused smithing consumed an input");
        }
    }

    private static ItemStack template() {
        return ForbiddenArcanusChecks.nativeItem("apply_modifier_smithing_template");
    }

    private static ItemStack addition(String kind) {
        return ForbiddenArcanusChecks.nativeItem(kind.equals("eternal") ? "eternal_stella" : "soul_binding_crystal");
    }
}
