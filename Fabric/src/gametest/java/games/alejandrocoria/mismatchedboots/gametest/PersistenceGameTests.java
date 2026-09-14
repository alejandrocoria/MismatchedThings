package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class PersistenceGameTests {
    private static final String RESTART_MODE_PROPERTY = "mismatchedboots.test.persistence.restartMode";
    private static final String RESTART_TOKEN_PROPERTY = "mismatchedboots.test.persistence.restartToken";
    private static final BlockPos RESTART_CHEST_POS = new BlockPos(8, 64, 8);
    private static final Identifier RESTART_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("mismatchedboots-test", "restart_rule");

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void containerRoundTripPreservesBothOrientationsAndOpaqueOwnership(GameTestHelper helper) {
        MixedBootsPair pair = persistedPair("container-alpha");
        helper.setBlock(BlockPos.ZERO, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(BlockPos.ZERO, ChestBlockEntity.class);
        chest.setItem(0, pair.primary());
        chest.setItem(1, pair.inverse());

        CompoundTag encoded = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity decoded = BlockEntity.loadStatic(
                chest.getBlockPos(), chest.getBlockState(), encoded, helper.getLevel().registryAccess());

        helper.assertTrue(decoded instanceof ChestBlockEntity, "Serialized chest did not decode as a chest");
        ChestBlockEntity decodedChest = (ChestBlockEntity) decoded;
        assertPersistedPrimary(helper, decodedChest.getItem(0), "container primary", "container-alpha");
        assertPersistedSecondary(helper, decodedChest.getItem(1), "container secondary", "container-alpha");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void playerInventoryRoundTripPreservesLiveRootAndSnapshots(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        ItemStack primary = persistedPair("inventory-alpha").primary();
        primary.setDamageValue(17);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("persisted inventory boots"));
        inventory.setItem(9, primary);

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        inventory.save(output.list("Inventory", ItemStackWithSlot.CODEC));
        CompoundTag encoded = output.buildResult();
        inventory.clearContent();
        inventory.load(TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), encoded)
                .listOrEmpty("Inventory", ItemStackWithSlot.CODEC));

        ItemStack decoded = inventory.getItem(9);
        assertPersistedPrimary(helper, decoded, "player inventory", "inventory-alpha");
        helper.assertValueEqual(decoded.getDamageValue(), 17, "player inventory root damage");
        helper.assertTrue(Component.literal("persisted inventory boots").equals(decoded.get(DataComponents.CUSTOM_NAME)),
                "Player inventory lost the live root custom name");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void deathDropEntityRoundTripPreservesMixedStack(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        zombie.setItemSlot(EquipmentSlot.FEET, persistedPair("drop-alpha").primary());
        zombie.setGuaranteedDrop(EquipmentSlot.FEET);

        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);

        AABB bounds = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(3.0);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, Entity::isAlive).stream()
                .filter(entity -> ModContent.isMixedBoots(entity.getItem()))
                .toList();
        helper.assertValueEqual(drops.size(), 1, "mixed boots death-drop entities");
        ItemEntity decoded = new ItemEntity(EntityTypes.ITEM, helper.getLevel());
        decoded.restoreFrom(drops.getFirst());

        assertPersistedPrimary(helper, decoded.getItem(), "death-drop entity", "drop-alpha");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void restartRoundTripUsesTheDedicatedTwoProcessHarness(GameTestHelper helper) {
        String mode = System.getProperty(RESTART_MODE_PROPERTY, "disabled");
        if (mode.equals("disabled")) {
            helper.succeed();
            return;
        }

        String token = System.getProperty(RESTART_TOKEN_PROPERTY);
        helper.assertTrue(token != null && !token.isBlank(), "Persistence restart token is missing");
        if (mode.equals("write")) {
            writeRestartFixture(helper.getLevel(), token);
        } else if (mode.equals("read")) {
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(RESTART_CHEST_POS);
            helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                    "The restart read process did not load the persisted chest");
            ItemStack restarted = ((ChestBlockEntity) blockEntity).getItem(0);
            assertPersistedPrimary(helper, restarted, "server restart", token);
            helper.assertValueEqual(restarted.getDamageValue(), 23, "server restart root damage");
            assertMovementAmount(restarted, 3.0D, "stored result after rule change");
            helper.assertValueEqual(AttributeRuleState.current().policyFor(
                            Identifier.withDefaultNamespace("movement_speed")),
                    AttributePolicy.COMBINE, "restart read process movement-speed policy");

            MixedBootsData storedSources = GameTestStacks.data(restarted);
            assertMovementAmount(storedSources.left().source(), 2.0D, "stored left snapshot");
            assertMovementAmount(storedSources.right().source(), 4.0D, "stored right snapshot");
            ItemStack newlyCrafted = restartPair(token).primary();
            assertMovementAmount(newlyCrafted, 6.0D, "new result after rule change");
        } else {
            throw new AssertionError("Unknown persistence restart mode: " + mode);
        }
        helper.succeed();
    }

    private static void writeRestartFixture(ServerLevel level, String token) {
        level.setBlockAndUpdate(RESTART_CHEST_POS, Blocks.CHEST.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(RESTART_CHEST_POS);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            throw new AssertionError("Could not create the restart persistence chest");
        }
        ItemStack stack = restartPair(token).primary();
        assertMovementAmount(stack, 3.0D, "write-process result");
        stack.setDamageValue(23);
        chest.setItem(0, stack);
        chest.setChanged();
    }

    private static MixedBootsPair persistedPair(String opaqueValue) {
        ItemStack owner = new ItemStack(TestContent.requireComponentBoots());
        owner.set(TestContent.requireOpaqueComponent(), opaqueValue);
        return GameTestStacks.pair(owner, new ItemStack(Items.DIAMOND_BOOTS));
    }

    private static MixedBootsPair restartPair(String opaqueValue) {
        ItemStack owner = new ItemStack(TestContent.requireComponentBoots());
        owner.set(TestContent.requireOpaqueComponent(), opaqueValue);
        owner.set(DataComponents.ATTRIBUTE_MODIFIERS, movementModifiers(2.0D));
        ItemStack other = new ItemStack(Items.DIAMOND_BOOTS);
        other.set(DataComponents.ATTRIBUTE_MODIFIERS, movementModifiers(4.0D));
        return GameTestStacks.pair(owner, other);
    }

    private static ItemAttributeModifiers movementModifiers(double amount) {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(RESTART_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build();
    }

    private static void assertMovementAmount(ItemStack stack, double expected, String description) {
        var entries = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.MOVEMENT_SPEED))
                .toList();
        if (entries.size() != 1
                || !entries.getFirst().modifier().id().equals(RESTART_MODIFIER_ID)
                || Math.abs(entries.getFirst().modifier().amount() - expected) >= 1.0E-12D) {
            throw new AssertionError(description + " did not contain movement speed " + expected + ": " + entries);
        }
    }

    private static void assertPersistedPrimary(
            GameTestHelper helper,
            ItemStack stack,
            String description,
            String opaqueValue) {
        GameTestStacks.assertOrientation(
                helper, stack, TestContent.requireComponentBoots(), Items.DIAMOND_BOOTS, description);
        helper.assertTrue(opaqueValue.equals(stack.get(TestContent.requireOpaqueComponent())),
                description + " lost the opaque owner component on the root");
        MixedBootsData data = GameTestStacks.data(stack);
        helper.assertTrue(opaqueValue.equals(data.left().source().get(TestContent.requireOpaqueComponent())),
                description + " lost the opaque component in its left snapshot");
        helper.assertTrue(data.right().source().get(TestContent.requireOpaqueComponent()) == null,
                description + " copied the opaque component into its right snapshot");
    }

    private static void assertPersistedSecondary(
            GameTestHelper helper,
            ItemStack stack,
            String description,
            String opaqueValue) {
        GameTestStacks.assertOrientation(
                helper, stack, Items.DIAMOND_BOOTS, TestContent.requireComponentBoots(), description);
        helper.assertTrue(stack.get(TestContent.requireOpaqueComponent()) == null,
                description + " copied a right-source component onto the root");
        MixedBootsData data = GameTestStacks.data(stack);
        helper.assertTrue(opaqueValue.equals(data.right().source().get(TestContent.requireOpaqueComponent())),
                description + " lost the opaque component in its right snapshot");
    }
}
