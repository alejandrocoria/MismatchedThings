package games.alejandrocoria.mismatchedboots.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("removal")
public final class CompatibilityGameTests {
    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void moddedComponentBootsCraftThroughTheLiveRecipe(GameTestHelper helper) {
        ItemStack modded = new ItemStack(TestContent.requireComponentBoots());
        modded.set(TestContent.requireOpaqueComponent(), "compatibility-alpha");
        CraftingInput input = CraftingInput.of(2, 2, List.of(
                modded, ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.DIAMOND_BOOTS)));
        var recipe = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("Live recipe rejected the GameTest modded boots"));

        ItemStack primary = recipe.value().assemble(input);
        NonNullList<ItemStack> remainders = recipe.value().getRemainingItems(input);
        ItemStack inverse = remainders.stream()
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Live recipe did not create the inverse remainder"));

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
    public void playerDeathAndRespawnPreserveExactlyOneDroppedMixedStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack mixed = GameTestStacks.pair(
                new ItemStack(TestContent.requireComponentBoots()), new ItemStack(Items.DIAMOND_BOOTS)).primary();
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
        try {
            player.setHealth(0.0F);
            player.die(helper.getLevel().damageSources().genericKill());

            List<ItemStack> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropBounds, Entity::isAlive)
                    .stream()
                    .filter(entity -> !existingDropIds.contains(entity.getId()))
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(ModContent.MIXED_BOOTS_KEY))
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
        } finally {
            gameRules.set(GameRules.KEEP_INVENTORY, keepInventory, server);
        }
        helper.succeed();
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
        helper.assertTrue(lines.stream().anyMatch(component ->
                        component.getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals(key)),
                description + " did not contain " + key + ": " + lines);
    }
}
