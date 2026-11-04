package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class SmithingChecks {
    static final BlockPos STATION = new BlockPos(0, 65, 0);
    private static final Identifier FIRST = Identifier.parse("leggings_skeleton_test:first");
    private static final Identifier SECOND = Identifier.parse("leggings_skeleton_test:second");

    private SmithingChecks() {
    }

    static void verify(MinecraftServer server) {
        vanilla(server);
        genericTrimTagControl(server);
        invalid(server);
        synthetic(server);
        if (Boolean.getBoolean("leggings.test.boots") && !Boolean.getBoolean("leggings.test.previousBoots")) {
            bootsPreviewGuard(server);
        }
        controls(server);
    }

    private static void vanilla(MinecraftServer server) {
        for (boolean inverse : List.of(false, true)) {
            for (boolean both : List.of(false, true)) {
                var player = RepairFixtures.player(server);
                var pair = MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS),
                        new ItemStack(both ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS));
                var root = decorate(server, inverse ? pair.inverse() : pair.primary());
                var original = root.copy();
                var menu = ready(player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2), root,
                        new ItemStack(Items.NETHERITE_INGOT, 2));
                var output = menu.getSlot(3).getItem();
                preserved(original, output);
                var data = MixedLeggingsStructure.validData(output).orElseThrow();
                require(data.left().source().is(both || !inverse ? Items.NETHERITE_LEGGINGS : Items.IRON_LEGGINGS)
                                && data.right().source().is(both || inverse ? Items.NETHERITE_LEGGINGS : Items.IRON_LEGGINGS),
                        "Netherite transformed the wrong ordered sources");
                var derived = MixedLeggingsFactory.createPair(data.left().source(), data.right().source()).primary();
                require(output.getMaxDamage() == derived.getMaxDamage()
                                && output.get(DataComponents.ATTRIBUTE_MODIFIERS).equals(derived.get(DataComponents.ATTRIBUTE_MODIFIERS))
                                && output.get(DataComponents.REPAIRABLE).equals(derived.get(DataComponents.REPAIRABLE)),
                        "Smithing did not refresh capacity, effective attributes or repair materials");
                require(ItemStack.matches(original, root), "Netherite preview mutated input");
                var recipeId = ((ResultContainer) menu.getSlot(3).container).getRecipeUsed().id().identifier();
                paid(player, menu, inverse, 1, 1);
                require(SmithingCredits.count(player, recipeId) == 1, "Two sources using one recipe were credited more than once");
            }

            var root = decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS),
                    new ItemStack(Items.GOLDEN_LEGGINGS)).primary());
            var player = RepairFixtures.player(server);
            var menu = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 2), root, new ItemStack(Items.DIAMOND, 2));
            var result = menu.getSlot(3).getItem();
            preserved(root, result);
            var data = MixedLeggingsStructure.validData(result).orElseThrow();
            require(data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM)
                            && !result.has(DataComponents.TRIM), "Native generic trim stayed invisible on the root instead of both sources");
            var trimmed = result.copy();
            paid(player, menu, inverse, 1, 1);
            var repeated = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), trimmed, new ItemStack(Items.DIAMOND));
            require(!repeated.getSlot(3).hasItem(), "No-op source trim still charged a root-only application");
        }
        // Only one ordinary source is trimmable; composition must not invent the other half.
        var paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_LEGGINGS).get(DataComponents.EQUIPPABLE));
        var player = RepairFixtures.player(server);
        var one = MixedLeggingsFactory.createPair(paper, new ItemStack(Items.IRON_LEGGINGS)).primary();
        var trim = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), one, new ItemStack(Items.DIAMOND));
        var data = MixedLeggingsStructure.validData(trim.getSlot(3).getItem()).orElseThrow();
        require(!data.left().source().has(DataComponents.TRIM) && data.right().source().has(DataComponents.TRIM), "One-sided trim became two-sided");
    }

    private static void genericTrimTagControl(MinecraftServer server) {
        var original = new LinkedHashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> original.put(tag.key(), tag.stream().toList()));
        try {
            // A datapack may explicitly opt the root into native generic trimming.
            var root = RepairFixtures.mixed(false);
            var members = new ArrayList<>(original.get(ItemTags.TRIMMABLE_ARMOR));
            members.add(root.typeHolder());
            var changed = new LinkedHashMap<>(original);
            changed.put(ItemTags.TRIMMABLE_ARMOR, members);
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
            require(root.is(ItemTags.TRIMMABLE_ARMOR), "Datapack trim control did not activate");
            var player = RepairFixtures.player(server);
            var menu = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), root, new ItemStack(Items.DIAMOND));
            var output = menu.getSlot(3).getItem();
            var data = MixedLeggingsStructure.validData(output).orElseThrow();
            require(!output.has(DataComponents.TRIM) && data.left().source().has(DataComponents.TRIM)
                            && data.right().source().has(DataComponents.TRIM),
                    "Datapack generic trim bypassed source-wise application");
            var repeated = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), output.copy(), new ItemStack(Items.DIAMOND));
            require(!repeated.getSlot(3).hasItem(), "Datapack generic trim admitted a root-only no-op");
        } finally {
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, original)).apply();
        }
    }

    private static void synthetic(MinecraftServer server) {
        var original = List.copyOf(server.getRecipeManager().getRecipes());
        var ironRecipe = transform(FIRST, Items.IRON_LEGGINGS, Items.IRON_LEGGINGS,
                DataComponentPatch.builder().set(CompositionFixture.CHARGE, 999L).set(DataComponents.RARITY, Rarity.RARE).build());
        var goldRecipe = transform(SECOND, Items.GOLDEN_LEGGINGS, Items.GOLDEN_LEGGINGS,
                DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build());
        try {
            install(server, original, ironRecipe, goldRecipe);
            for (boolean inverse : List.of(false, true)) {
                var player = RepairFixtures.player(server);
                var source = new ItemStack(Items.IRON_LEGGINGS);
                source.set(CompositionFixture.CHARGE, 900L);
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.GOLDEN_LEGGINGS));
                var root = decorate(server, inverse ? pair.inverse() : pair.primary());
                var before = root.copy();
                var menu = ready(player, ItemStack.EMPTY, root, new ItemStack(Items.AMETHYST_SHARD, 2));
                preserved(before, menu.getSlot(3).getItem());
                require(!menu.getSlot(3).getItem().has(DataComponents.RARITY), "Unchanged managed projection did not follow the new omission");
                require(SmithingCredits.count(player, FIRST) == 0 && SmithingCredits.count(player, SECOND) == 0,
                        "Preview credited source recipes before extraction");
                paid(player, menu, inverse, 0, 1);
                require(SmithingCredits.count(player, FIRST) == 1 && SmithingCredits.count(player, SECOND) == 1,
                        "Distinct source recipes were not both credited exactly once");

                var removed = before.copy();
                removed.remove(CompositionFixture.CHARGE);
                var removal = ready(player, ItemStack.EMPTY, removed, new ItemStack(Items.AMETHYST_SHARD));
                require(!removal.getSlot(3).getItem().has(CompositionFixture.CHARGE), "Recomposition resurrected a deliberately removed live resource");
                var edited = before.copy();
                edited.set(DataComponents.RARITY, Rarity.UNCOMMON);
                require(ready(player, ItemStack.EMPTY, edited, new ItemStack(Items.AMETHYST_SHARD)).getSlot(3).getItem().get(DataComponents.RARITY) == Rarity.UNCOMMON,
                        "New source projection overwrote a deliberately edited live field");
                var wrongTemplate = ready(player, new ItemStack(Items.PAPER), before.copy(), new ItemStack(Items.AMETHYST_SHARD));
                require(!wrongTemplate.getSlot(3).hasItem(), "Empty-template recipe ignored a populated template slot");
            }
            stalePreviews(server, original, ironRecipe, goldRecipe);

            var player = RepairFixtures.player(server);
            var rootItem = BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID);
            var dedicated = transform(Identifier.parse("leggings_skeleton_test:direct"), rootItem, rootItem, DataComponentPatch.builder()
                    .set(DataComponents.CUSTOM_NAME, Component.literal("Dedicated root result")).build());
            var oldPriority = ready(player, ItemStack.EMPTY, decorate(server, MixedLeggingsFactory.createPair(
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary()), new ItemStack(Items.AMETHYST_SHARD));
            install(server, original, ironRecipe, goldRecipe, dedicated);
            require(oldPriority.quickMoveStack(player, 3).isEmpty(), "A newly loaded dedicated root recipe did not invalidate generic fallback priority");
            install(server, original, dedicated, goldRecipe);
            var direct = ready(player, ItemStack.EMPTY, decorate(server, MixedLeggingsFactory.createPair(
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary()), new ItemStack(Items.AMETHYST_SHARD));
            require(direct.getSlot(3).getItem().getHoverName().getString().equals("Dedicated root result"), "Generic fallback replaced a dedicated native root result");

            var shrinking = transform(FIRST, Items.IRON_LEGGINGS, Items.IRON_LEGGINGS,
                    DataComponentPatch.builder().set(DataComponents.MAX_DAMAGE, 40).build());
            install(server, original, shrinking);
            var worn = decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary());
            var clamped = ready(player, ItemStack.EMPTY, worn, new ItemStack(Items.AMETHYST_SHARD)).getSlot(3).getItem();
            require(worn.getDamageValue() > 40 && clamped.getMaxDamage() == 40 && clamped.getDamageValue() == 40,
                    "Recomposition failed to clamp paid damage to a smaller new capacity");

            for (var target : List.of(new ItemStackTemplate(Items.IRON_LEGGINGS), new ItemStackTemplate(Items.IRON_BOOTS),
                    new ItemStackTemplate(Items.IRON_LEGGINGS, 2))) {
                var invalid = new RecipeHolder<SmithingRecipe>(ResourceKey.create(Registries.RECIPE, FIRST),
                        new SmithingTransformRecipe(new Recipe.CommonInfo(false), Optional.empty(), Ingredient.of(Items.IRON_LEGGINGS),
                                Optional.of(Ingredient.of(Items.AMETHYST_SHARD)), target));
                install(server, original, invalid);
                var root = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary();
                require(!ready(player, ItemStack.EMPTY, root, new ItemStack(Items.AMETHYST_SHARD)).getSlot(3).hasItem(),
                        "No-op, wrong-slot or multi-count source transformation was admitted");
            }
        } finally {
            WorkbenchChecks.applyRecipes(server, original);
        }
    }

    private static void bootsPreviewGuard(MinecraftServer server) {
        var original = List.copyOf(server.getRecipeManager().getRecipes());
        var first = transform(FIRST, Items.IRON_BOOTS, Items.IRON_BOOTS,
                DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.RARE).build());
        var second = transform(SECOND, Items.GOLDEN_BOOTS, Items.GOLDEN_BOOTS,
                DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build());
        var rootItem = BootsCoexistence.mixed().getItem();
        var emptyRoot = new RecipeHolder<SmithingRecipe>(ResourceKey.create(Registries.RECIPE,
                Identifier.parse("leggings_skeleton_test:empty_boots_root")),
                new SmithingTransformRecipe(new Recipe.CommonInfo(false), Optional.empty(), Ingredient.of(rootItem),
                        Optional.of(Ingredient.of(Items.AMETHYST_SHARD)), new ItemStackTemplate(rootItem)) {
                    @Override
                    public ItemStack assemble(SmithingRecipeInput input) {
                        return ItemStack.EMPTY;
                    }
                });
        var dedicated = transform(Identifier.parse("leggings_skeleton_test:direct_boots_root"), rootItem, rootItem,
                DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Dedicated Boots result")).build());
        try {
            for (boolean shift : List.of(false, true)) {
                // A matching native root recipe with an empty result must still permit fallback.
                install(server, original, first, second, emptyRoot);
                var player = RepairFixtures.player(server);
                var root = BootsCoexistence.mixed();
                var menu = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD, 2));
                require(menu.getSlot(3).hasItem() && SmithingCredits.count(player, FIRST) == 0
                                && SmithingCredits.count(player, SECOND) == 0,
                        "Boots source-wise preview was missing or credited recipes before extraction");
                paid(player, menu, shift, 0, 1);
                require(SmithingCredits.count(player, FIRST) == 1 && SmithingCredits.count(player, SECOND) == 1,
                        "Boots pickup validation lost or duplicated distinct source recipe credits");

                install(server, original, first, second);
                var stale = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD, 2));
                // Preserve both source holder objects: only the newly added root priority changes.
                install(server, original, first, second, dedicated);
                var taken = shift ? stale.quickMoveStack(player, 3) : stale.getSlot(3).safeTake(1, 1, player);
                require(taken.isEmpty() && ItemStack.matches(root, stale.getSlot(1).getItem())
                                && stale.getSlot(2).getItem().getCount() == 2,
                        "A newly loaded dedicated root recipe did not block the old Boots fallback");
                var direct = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD, 2));
                require(direct.getSlot(3).getItem().getHoverName().getString().equals("Dedicated Boots result"),
                        "Boots source validation replaced a dedicated native root result");
                paid(player, direct, shift, 0, 1);
                require(SmithingCredits.count(player, dedicated.id().identifier()) == 1,
                        "Dedicated native Boots recipe was not credited exactly once");
            }
        } finally {
            WorkbenchChecks.applyRecipes(server, original);
        }
    }

    @SafeVarargs
    private static void install(MinecraftServer server, List<RecipeHolder<?>> original, RecipeHolder<SmithingRecipe>... recipes) {
        var changed = new ArrayList<RecipeHolder<?>>(original);
        changed.addAll(List.of(recipes));
        WorkbenchChecks.applyRecipes(server, changed);
    }

    private static void stalePreviews(MinecraftServer server, List<RecipeHolder<?>> original,
                                      RecipeHolder<SmithingRecipe> first, RecipeHolder<SmithingRecipe> second) {
        var player = RepairFixtures.player(server);
        var root = decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary());
        var normal = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD));
        var shifted = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD));
        WorkbenchChecks.applyRecipes(server, original);
        require(normal.getSlot(3).safeTake(1, 1, player).isEmpty() && shifted.quickMoveStack(player, 3).isEmpty()
                        && normal.getSlot(1).hasItem() && normal.getSlot(2).hasItem() && shifted.getSlot(1).hasItem(),
                "Removed recipes allowed stale smithing payment");
        install(server, original, first, second);
        var replaced = ready(player, ItemStack.EMPTY, root.copy(), new ItemStack(Items.AMETHYST_SHARD));
        install(server, original, new RecipeHolder<>(first.id(), new SmithingTransformRecipe(new Recipe.CommonInfo(false), Optional.empty(),
                Ingredient.of(Items.IRON_LEGGINGS), Optional.of(Ingredient.of(Items.AMETHYST_SHARD)),
                new ItemStackTemplate(Items.IRON_LEGGINGS, DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build()))), second);
        require(replaced.quickMoveStack(player, 3).isEmpty() && replaced.getSlot(1).hasItem(), "Replaced recipe holder allowed a stale preview");
    }

    private static void invalid(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var root = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        require(!ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), root, new ItemStack(Items.DIAMOND)).getSlot(3).hasItem(),
                "Bare root admitted native tag-based trim");
        var malformed = decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)).primary());
        malformed.remove(DataComponents.EQUIPPABLE);
        require(!ready(player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), malformed, new ItemStack(Items.NETHERITE_INGOT)).getSlot(3).hasItem(),
                "Malformed root entered source-wise smithing");
    }

    private static void controls(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        var nativeMenu = ready(player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.NETHERITE_INGOT));
        require(nativeMenu.getSlot(3).getItem().is(Items.NETHERITE_LEGGINGS), "Native smithing was intercepted");
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.mixed();
            var menu = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), boots, new ItemStack(Items.DIAMOND));
            require(menu.getSlot(3).getItem().getItem() == boots.getItem(), "Leggings suppressed Boots source smithing");
            paid(player, menu, true, 0, 0);
        }
        var root = decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)).primary());
        var cancelled = ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), root, new ItemStack(Items.DIAMOND));
        var beforeClose = root.copy();
        player.getInventory().clearContent();
        cancelled.removed(player);
        require(WorkbenchChecks.inventory(player).size() == 3 && WorkbenchChecks.contains(player, beforeClose), "Unpaid close lost smithing inputs");
    }

    private static RecipeHolder<SmithingRecipe> transform(Identifier id, Item source, Item target, DataComponentPatch patch) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), new SmithingTransformRecipe(new Recipe.CommonInfo(false),
                Optional.empty(), Ingredient.of(source), Optional.of(Ingredient.of(Items.AMETHYST_SHARD)), new ItemStackTemplate(target, patch)));
    }

    static ItemStack decorate(MinecraftServer server, ItemStack root) {
        root.setDamageValue(Math.min(80, root.getMaxDamage() - 1));
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Live smithing root"));
        root.set(DataComponents.REPAIR_COST, 17);
        root.set(CompositionFixture.CHARGE, 123L);
        var unbreaking = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        root.enchant(unbreaking, 3);
        return root;
    }

    static SmithingMenu ready(ServerPlayer player, ItemStack template, ItemStack root, ItemStack addition) {
        var menu = new SmithingMenu(91, player.getInventory(), ContainerLevelAccess.create(player.level(), STATION));
        require(menu.getSlot(1).mayPlace(root), "Native base property set rejected mixed leggings");
        menu.getSlot(0).set(template);
        menu.getSlot(1).set(root);
        menu.getSlot(2).set(addition);
        return menu;
    }

    static void preserved(ItemStack original, ItemStack result) {
        require(MixedLeggingsStructure.validData(result).isPresent()
                        && original.get(DataComponents.CUSTOM_NAME).equals(result.get(DataComponents.CUSTOM_NAME))
                        && original.get(DataComponents.ENCHANTMENTS).equals(result.get(DataComponents.ENCHANTMENTS))
                        && original.get(DataComponents.REPAIR_COST).equals(result.get(DataComponents.REPAIR_COST))
                        && result.getDamageValue() == Math.min(original.getDamageValue(), result.getMaxDamage())
                        && result.get(CompositionFixture.CHARGE).equals(123L), "Smithing reset live name, enchantments, damage, prior work or resources");
    }

    private static void paid(ServerPlayer player, SmithingMenu menu, boolean shift, int templatesLeft, int additionsLeft) {
        var preview = menu.getSlot(3).getItem().copy();
        var taken = shift ? menu.quickMoveStack(player, 3) : menu.getSlot(3).safeTake(1, 1, player);
        require(!taken.isEmpty() && ItemStack.matches(preview, taken) && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == templatesLeft && menu.getSlot(2).getItem().getCount() == additionsLeft
                        && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Smithing output/payment differed from preview or repeated");
    }
}
