package games.alejandrocoria.mismatchedboots.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.tooltip.TooltipSeparator;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("removal")
public final class CompatibilityGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void moddedComponentBootsComposeThroughTheLiveWorkbenchRecipe(GameTestHelper helper) {
        ItemStack modded = new ItemStack(TestContent.requireComponentBoots());
        modded.set(TestContent.requireOpaqueComponent(), "compatibility-alpha");
        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(modded, new ItemStack(Items.DIAMOND_BOOTS));
        var recipe = helper.getLevel().recipeAccess()
                           .getRecipeFor(ModContent.mismatchedWorkbenchRecipeType(), input, helper.getLevel())
                           .orElseThrow(() -> new AssertionError("Live workbench recipe rejected the GameTest modded boots"));

        var result = recipe.value().assemblePair(input);
        helper.assertTrue(result.isComplete(), "Live workbench recipe did not create both outputs");
        ItemStack primary = result.primary();
        ItemStack inverse = result.inverse();

        GameTestStacks.assertOrientation(helper, primary,
                                         TestContent.requireComponentBoots(), Items.DIAMOND_BOOTS, "modded primary");
        GameTestStacks.assertOrientation(helper, inverse,
                                         Items.DIAMOND_BOOTS, TestContent.requireComponentBoots(), "modded inverse");
        helper.assertTrue("compatibility-alpha".equals(primary.get(TestContent.requireOpaqueComponent())),
                          "Primary output lost the modded owner's opaque component");
        helper.assertTrue(inverse.get(TestContent.requireOpaqueComponent()) == null,
                          "Inverse output copied opaque data from its non-owner source");
        helper.assertTrue("compatibility-alpha".equals(
                                  GameTestStacks.data(inverse).right().source().get(TestContent.requireOpaqueComponent())),
                          "Inverse output lost opaque data from its nested modded source");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void commandsTooltipsAndCreativeInventoryHandleBareAndValidStacks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER),
                "give @s mismatchedboots:mixed_boots");
        List<ItemStack> commandStacks = player.getInventory().getNonEquipmentItems().stream()
                                              .filter(stack -> stack.is(ModContent.MIXED_BOOTS_KEY))
                                              .toList();
        helper.assertValueEqual(commandStacks.size(), 1, "command-given mixed stacks");
        helper.assertTrue(ModContent.getMixedBootsData(commandStacks.getFirst()).isEmpty(),
                          "The bare command stack unexpectedly acquired source data");

        List<Component> invalidTooltip = commandStacks.getFirst().getTooltipLines(
                Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL);
        assertTranslation(helper, invalidTooltip,
                          "tooltip.mismatchedboots.mixed_boots.invalid", "invalid-stack tooltip");
        helper.assertTrue(indexOfTranslation(invalidTooltip,
                                             "tooltip.mismatchedboots.mixed_boots.left") < 0
                                  && indexOfTranslation(invalidTooltip,
                                                        "tooltip.mismatchedboots.mixed_boots.right") < 0,
                          "Invalid stack tooltip exposed partial source regions: " + invalidTooltip);

        ItemStack left = new ItemStack(TestContent.requireComponentBoots());
        left.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture Left"));
        ItemStack right = new ItemStack(Items.DIAMOND_BOOTS);
        right.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture Right"));
        ItemStack valid = GameTestStacks.pair(left, right).primary();
        List<Component> validTooltip = valid.getTooltipLines(
                Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL);
        assertTranslation(helper, validTooltip,
                          "tooltip.mismatchedboots.mixed_boots.left", "valid left tooltip");
        assertTranslation(helper, validTooltip,
                          "tooltip.mismatchedboots.mixed_boots.right", "valid right tooltip");
        helper.assertTrue(containsBoldTranslation(validTooltip,
                                                  "tooltip.mismatchedboots.mixed_boots.left")
                                  && containsBoldTranslation(validTooltip,
                                                             "tooltip.mismatchedboots.mixed_boots.right"),
                          "Valid source labels were not bold: " + validTooltip);

        ItemStack ordered = GameTestStacks.pair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).primary();
        ItemStack orderedBefore = ordered.copy();
        List<Component> orderedTooltip = ordered.getTooltipLines(
                Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL);
        int leftIndex = indexOfTranslation(orderedTooltip, "tooltip.mismatchedboots.mixed_boots.left");
        int rightIndex = indexOfTranslation(orderedTooltip, "tooltip.mismatchedboots.mixed_boots.right");
        int rootAttributesIndex = indexOfTranslation(orderedTooltip, "item.modifiers.feet");
        helper.assertTrue(leftIndex > 0 && rightIndex > leftIndex && rootAttributesIndex > rightIndex,
                          "Source labels did not precede root details: " + orderedTooltip);
        helper.assertTrue(ItemStack.matches(orderedBefore, ordered),
                          "Tooltip generation mutated the mixed stack");

        CreativeModeTabs.tryRebuildTabContents(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        List<ItemStack> creativeEntries = CreativeModeTabs.allTabs().stream()
                                                          .flatMap(tab -> tab.getSearchTabDisplayItems().stream())
                                                          .toList();
        helper.assertTrue(creativeEntries.stream().anyMatch(stack -> stack.is(Items.DIAMOND_BOOTS)),
                          "Creative tab control item was unavailable");
        helper.assertTrue(creativeEntries.stream().noneMatch(stack -> stack.is(ModContent.MIXED_BOOTS_KEY)),
                          "Creative tabs exposed a bare mixed stack without source data");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void registeredWorkbenchOutputsExposeSourceRegionsAndLiveRootDetails(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var registryAccess = helper.getLevel().registryAccess();
        var trimMaterials = registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL);
        var trimPatterns = registryAccess.lookupOrThrow(Registries.TRIM_PATTERN);
        var enchantments = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        var leftPattern = trimPatterns.getOrThrow(TrimPatterns.COAST);
        var rightPattern = trimPatterns.getOrThrow(TrimPatterns.DUNE);
        var rootPattern = trimPatterns.getOrThrow(TrimPatterns.RIB);
        ArmorTrim leftTrim = new ArmorTrim(
                trimMaterials.getOrThrow(TrimMaterials.QUARTZ), leftPattern);
        ArmorTrim rightTrim = new ArmorTrim(
                trimMaterials.getOrThrow(TrimMaterials.REDSTONE), rightPattern);
        ArmorTrim rootTrim = new ArmorTrim(
                trimMaterials.getOrThrow(TrimMaterials.GOLD), rootPattern);
        var unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);

        ItemStack left = new ItemStack(Items.LEATHER_BOOTS);
        left.set(DataComponents.TRIM, leftTrim);
        left.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699));
        left.setDamageValue(20);
        setEnchantment(left, unbreaking, 1);
        ItemStack right = new ItemStack(Items.GOLDEN_BOOTS);
        right.set(DataComponents.TRIM, rightTrim);
        right.setDamageValue(10);
        setEnchantment(right, unbreaking, 2);
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();

        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(left, right);
        var recipe = helper.getLevel().recipeAccess()
                           .getRecipeFor(ModContent.mismatchedWorkbenchRecipeType(), input, helper.getLevel())
                           .orElseThrow(() -> new AssertionError("Live workbench recipe rejected tooltip fixtures"));
        var result = recipe.value().assemblePair(input);
        helper.assertTrue(result.isComplete(), "Live workbench recipe did not create tooltip outputs");
        ItemStack primary = result.primary();
        ItemStack inverse = result.inverse();
        primary.set(DataComponents.TRIM, rootTrim);
        inverse.set(DataComponents.TRIM, rootTrim);
        Component rootName = Component.literal("Renamed Mismatched Boots");
        primary.set(DataComponents.CUSTOM_NAME, rootName);
        inverse.set(DataComponents.CUSTOM_NAME, rootName);
        ItemStack primaryBefore = primary.copy();
        ItemStack inverseBefore = inverse.copy();
        MixedBootsData primaryDataBefore = GameTestStacks.data(primary);
        MixedBootsData inverseDataBefore = GameTestStacks.data(inverse);
        Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());

        List<Component> primaryNormal = primary.getTooltipLines(context, player, TooltipFlag.NORMAL);
        List<Component> inverseNormal = inverse.getTooltipLines(context, player, TooltipFlag.NORMAL);
        List<Component> primaryAdvanced = primary.getTooltipLines(context, player, TooltipFlag.ADVANCED);

        helper.assertTrue(primaryNormal.getFirst().equals(primary.getStyledHoverName())
                                  && primaryNormal.getFirst().getString().equals(rootName.getString()),
                          "Renamed root title was not kept separate from source identities: " + primaryNormal);
        assertTooltipLayout(helper, primaryNormal, leftPattern.value().description(),
                            rightPattern.value().description(), rootPattern.value().description(), unbreaking,
                            primary.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking),
                            "primary");
        assertTooltipLayout(helper, inverseNormal, rightPattern.value().description(),
                            leftPattern.value().description(), rootPattern.value().description(), unbreaking,
                            inverse.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking),
                            "inverse");

        helper.assertValueEqual(countTranslation(primaryAdvanced, "item.durability"), 1L,
                                "advanced live-root durability lines");
        helper.assertValueEqual(countTranslation(primaryAdvanced, "item.components"), 1L,
                                "advanced live-root component-count lines");
        helper.assertValueEqual(countTranslation(primaryNormal, "item.dyed"), 1L,
                                "normal source dyed-marker lines");
        helper.assertValueEqual(countTranslation(primaryAdvanced, "item.color"), 1L,
                                "advanced source hexadecimal-color lines");
        helper.assertValueEqual(countTranslation(primaryAdvanced, "item.dyed"), 0L,
                                "advanced source dyed-marker lines");
        helper.assertTrue(indexOfText(primaryAdvanced, ModContent.MIXED_BOOTS_ID.toString()) >= 0,
                          "Advanced tooltip omitted the registered root item ID: " + primaryAdvanced);
        for (String sourceId : List.of("minecraft:leather_boots", "minecraft:golden_boots")) {
            helper.assertValueEqual(primaryAdvanced.stream().filter(line -> line.getString().equals(sourceId)).count(),
                                    1L, "advanced source ID count: " + sourceId);
            helper.assertTrue(indexOfText(primaryNormal, sourceId) < 0,
                              "Normal tooltip exposed an advanced source ID: " + sourceId);
        }

        helper.assertTrue(ItemStack.matches(leftBefore, left) && ItemStack.matches(rightBefore, right),
                          "Workbench tooltip fixture inputs were mutated");
        helper.assertTrue(ItemStack.matches(primaryBefore, primary)
                                  && ItemStack.matches(inverseBefore, inverse),
                          "Tooltip generation mutated a registered workbench output");
        helper.assertTrue(primaryDataBefore.equals(GameTestStacks.data(primary))
                                  && inverseDataBefore.equals(GameTestStacks.data(inverse)),
                          "Tooltip generation mutated source snapshots");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void playerDeathAndRespawnPreserveExactlyOneDroppedMixedStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack mixed = GameTestStacks.pair(
                new ItemStack(TestContent.requireComponentBoots()), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        Component deathFixtureName = Component.literal("Death Fixture " + UUID.randomUUID());
        mixed.set(DataComponents.CUSTOM_NAME, deathFixtureName);
        MixedBootsData snapshots = GameTestStacks.data(mixed);
        player.getInventory().setItem(9, mixed);
        AABB dropBounds = player.getBoundingBox().inflate(3.0);
        Set<Integer> existingDropIds = helper.getLevel()
                                             .getEntitiesOfClass(ItemEntity.class, dropBounds, Entity::isAlive)
                                             .stream()
                                             .map(Entity::getId)
                                             .collect(Collectors.toSet());

        var server = helper.getLevel().getServer();
        GameRules gameRules = helper.getLevel().getGameRules();
        boolean keepInventory = gameRules.get(GameRules.KEEP_INVENTORY);
        gameRules.set(GameRules.KEEP_INVENTORY, false, server);
        player.die(helper.getLevel().damageSources().genericKill());

        helper.runAfterDelay(1, () -> {
            try {
                List<ItemStack> drops = helper.getLevel()
                                              .getEntitiesOfClass(ItemEntity.class, dropBounds, Entity::isAlive)
                                              .stream()
                                              .filter(entity -> !existingDropIds.contains(entity.getId()))
                                              .map(ItemEntity::getItem)
                                              .filter(stack -> stack.is(ModContent.MIXED_BOOTS_KEY))
                                              .filter(stack -> deathFixtureName.equals(stack.get(DataComponents.CUSTOM_NAME)))
                                              .toList();
                helper.assertValueEqual(drops.size(), 1, "player-death mixed drops");
                GameTestStacks.assertOrientation(helper, drops.getFirst(),
                                                 TestContent.requireComponentBoots(), Items.DIAMOND_BOOTS, "player-death drop");
                helper.assertTrue(GameTestStacks.data(drops.getFirst()).equals(snapshots),
                                  "Player death changed the nested source snapshots");

                ServerPlayer respawned = server.getPlayerList()
                                               .respawn(player, false, Entity.RemovalReason.KILLED);
                helper.assertTrue(
                        respawned.getInventory().getNonEquipmentItems().stream().noneMatch(ModContent::isMixedBoots),
                        "Respawn duplicated the dropped mixed stack into the player inventory");
                helper.succeed();
            } finally {
                gameRules.set(GameRules.KEEP_INVENTORY, keepInventory, server);
            }
        });
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void recursivePersistentAndNetworkDataFailAtTheCodecBoundary(GameTestHelper helper) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType().orElseThrow();
        MixedBootsData ordinaryData = GameTestStacks.data(
                GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        JsonObject encoded = MixedBootsData.CODEC.encodeStart(ops, ordinaryData)
                                                 .getOrThrow()
                                                 .getAsJsonObject();
        JsonObject nested = encoded.deepCopy();
        JsonObject leftSource = nested.getAsJsonObject("left");
        JsonObject components = leftSource.has("components")
                                ? leftSource.getAsJsonObject("components")
                                : new JsonObject();
        components.add(ModContent.MIXED_BOOTS_DATA_ID.toString(), encoded.deepCopy());
        leftSource.add("components", components);

        var persistentResult = MixedBootsData.CODEC.parse(ops, nested);
        helper.assertTrue(persistentResult.isError(), "Persistent codec accepted recursive mixed data");
        helper.assertTrue(persistentResult.error().orElseThrow().message().contains("cannot be nested"),
                          "Persistent recursive failure did not come from the nesting boundary: "
                                  + persistentResult.error().orElseThrow().message());

        ItemStack nestedSource = new ItemStack(Items.IRON_BOOTS);
        nestedSource.set(dataType, ordinaryData);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, nestedSource);
            ItemStack.STREAM_CODEC.encode(buffer, new ItemStack(Items.GOLDEN_BOOTS));
            try {
                MixedBootsData.STREAM_CODEC.decode(buffer);
                throw new AssertionError("Network codec accepted recursive mixed data");
            } catch (IllegalArgumentException exception) {
                helper.assertTrue(exception.getMessage().contains("cannot be nested"),
                                  "Network recursive failure did not come from the nesting boundary: " + exception);
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void assertTranslation(
            GameTestHelper helper, List<Component> lines, String key, String description) {
        helper.assertTrue(lines.stream().anyMatch(component -> containsTranslation(component, key)),
                          description + " did not contain " + key + ": " + lines);
    }

    private static int indexOfTranslation(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static void assertTooltipLayout(
            GameTestHelper helper,
            List<Component> lines,
            Component expectedLeftTrim,
            Component expectedRightTrim,
            Component expectedRootTrim,
            Holder<Enchantment> rootEnchantment,
            int rootEnchantmentLevel,
            String description) {
        int left = indexOfTranslation(lines, "tooltip.mismatchedboots.mixed_boots.left");
        int right = indexOfTranslation(lines, "tooltip.mismatchedboots.mixed_boots.right");
        int leftTrim = indexContainingComponent(lines, expectedLeftTrim);
        int rightTrim = indexContainingComponent(lines, expectedRightTrim);
        int rootTrim = lastIndexContainingComponent(lines, expectedRootTrim);
        int rootAttributes = indexOfTranslation(lines, "item.modifiers.feet");
        Component effectiveEnchantment = Enchantment.getFullname(rootEnchantment, rootEnchantmentLevel);

        helper.assertTrue(left == 1 && leftTrim > left,
                          description + " left source region was misplaced: " + lines);
        helper.assertTrue(right > leftTrim && rightTrim > right,
                          description + " right source region was misplaced: " + lines);
        helper.assertTrue(containsBoldTranslation(lines.get(left),
                                                  "tooltip.mismatchedboots.mixed_boots.left")
                                  && containsBoldTranslation(lines.get(right),
                                                             "tooltip.mismatchedboots.mixed_boots.right"),
                          description + " source labels were not bold: " + lines);
        helper.assertTrue(rootTrim > rightTrim && rootAttributes > rootTrim,
                          description + " live root region was misplaced: " + lines);
        helper.assertTrue(hasSeparatorBetween(lines, leftTrim, right)
                                  && hasSeparatorBetween(lines, rightTrim, rootTrim),
                          description + " source boundaries lacked separators: " + lines);
        helper.assertValueEqual(countTranslation(lines, "item.minecraft.smithing_template.upgrade"), 3L,
                                description + " trim section count");
        helper.assertValueEqual(countTranslation(lines, "item.modifiers.feet"), 1L,
                                description + " live-root attribute section count");
        helper.assertValueEqual(lines.stream().filter(effectiveEnchantment::equals).count(), 1L,
                                description + " effective enchantment line count");
    }

    private static void setEnchantment(
            ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        values.set(enchantment, level);
        stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
    }

    private static boolean hasSeparatorBetween(List<Component> lines, int startExclusive, int endExclusive) {
        for (int index = startExclusive + 1; index < endExclusive; index++) {
            if (lines.get(index) == TooltipSeparator.INSTANCE) {
                return true;
            }
        }
        return false;
    }

    private static long countTranslation(List<Component> lines, String key) {
        return lines.stream().filter(line -> containsTranslation(line, key)).count();
    }

    private static int indexContainingComponent(List<Component> lines, Component expected) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsComponent(lines.get(index), expected)) {
                return index;
            }
        }
        return -1;
    }

    private static int lastIndexContainingComponent(List<Component> lines, Component expected) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (containsComponent(lines.get(index), expected)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsComponent(Component component, Component expected) {
        if (component.getContents().equals(expected.getContents())) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsComponent(nested, expected)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsComponent(sibling, expected));
    }

    private static boolean containsTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)) {
                return true;
            }
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsTranslation(sibling, key));
    }

    private static boolean containsBoldTranslation(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> containsBoldTranslation(line, key));
    }

    private static boolean containsBoldTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key)
                && component.getStyle().isBold()) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsBoldTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream()
                        .anyMatch(sibling -> containsBoldTranslation(sibling, key));
    }

    private static int indexOfText(List<Component> lines, String text) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).getString().equals(text)) {
                return index;
            }
        }
        return -1;
    }
}
