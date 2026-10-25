package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
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
    private static final int PRIMARY_DAMAGE = 23;
    private static final int INVERSE_DAMAGE = 31;
    private static final int PRIMARY_REPAIR_COST = 15;

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
        var player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
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
        var player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, BlockPos.ZERO);
        zombie.setItemSlot(EquipmentSlot.FEET, persistedPair("drop-alpha").primary());
        zombie.setGuaranteedDrop(EquipmentSlot.FEET);

        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);

        AABB bounds = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(3.0);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, Entity::isAlive).stream()
                                       .filter(entity -> ModContent.isMixedBoots(entity.getItem()))
                                       .toList();
        helper.assertValueEqual(drops.size(), 1, "mixed boots death-drop entities");
        ItemEntity decoded = new ItemEntity(EntityType.ITEM, helper.getLevel());
        decoded.restoreFrom(drops.getFirst());

        assertPersistedPrimary(helper, decoded.getItem(), "death-drop entity", "drop-alpha");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void restartRoundTripUsesTheDedicatedTwoProcessHarness(GameTestHelper helper) {
        String mode = System.getProperty(RESTART_MODE_PROPERTY, "disabled");
        if (!GameTestContexts.requireOrSkip(helper, RESTART_MODE_PROPERTY, !mode.equals("disabled"))) {
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
            ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
            ItemStack primary = chest.getItem(0);
            ItemStack inverse = chest.getItem(1);
            assertRestartedRoot(helper, primary, true, token);
            assertRestartedRoot(helper, inverse, false, token);
            assertExplicitResistancePair(helper, chest.getItem(2), true);
            assertExplicitResistancePair(helper, chest.getItem(3), false);
            assertNamedResistancePair(helper, chest.getItem(4), "named primary");
            assertNamedResistancePair(helper, chest.getItem(5), "named inverse");

            helper.assertValueEqual(AttributeRulesLifecycle.serverRules().policyFor(
                                            Identifier.withDefaultNamespace("movement_speed")),
                                    AttributePolicy.COMBINE, "restart read process movement-speed policy");
            helper.assertValueEqual(ComponentRulesLifecycle.serverRules().durability(),
                                    DurabilityPolicy.SEPARATE, "restart read durability policy");
            helper.assertValueEqual(ComponentRulesLifecycle.serverRules().repairCost(),
                                    RepairCostPolicy.OWNER, "restart read repair-cost policy");
            helper.assertValueEqual(ComponentRulesLifecycle.serverRules().declarations().get(TestContent.NUMERIC_COMPONENT_ID),
                                    ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT),
                                    "restart read numeric component rule");

            assertSmithingAfterRuleChange(helper, primary, true, token);
            assertSmithingAfterRuleChange(helper, inverse, false, token);

            MixedBootsPair newlyCrafted = restartPair(token);
            assertMovementAmount(newlyCrafted.primary(), 6.0D, "new primary after rule change");
            assertMovementAmount(newlyCrafted.inverse(), 6.0D, "new inverse after rule change");
            helper.assertValueEqual(newlyCrafted.primary().getMaxDamage(),
                                    new ItemStack(TestContent.requireComponentBoots()).getMaxDamage(),
                                    "new primary separate maximum");
            helper.assertValueEqual(newlyCrafted.inverse().getMaxDamage(),
                                    new ItemStack(Items.IRON_BOOTS).getMaxDamage(),
                                    "new inverse separate maximum");
            helper.assertValueEqual(newlyCrafted.primary().getOrDefault(DataComponents.REPAIR_COST, 0), 2,
                                    "new primary owner repair cost");
            helper.assertValueEqual(newlyCrafted.inverse().getOrDefault(DataComponents.REPAIR_COST, 0), 7,
                                    "new inverse owner repair cost");
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
        MixedBootsPair pair = restartPair(token);
        assertProjectedRestartComponents(pair.primary(), token, "write-process primary");
        assertProjectedRestartComponents(pair.inverse(), token, "write-process inverse");
        preparePersistedRoot(pair.primary(), true, token);
        preparePersistedRoot(pair.inverse(), false, token);
        assertMovementAmount(pair.primary(), 3.0D, "write-process primary result");
        assertMovementAmount(pair.inverse(), 3.0D, "write-process inverse result");
        chest.setItem(0, pair.primary());
        chest.setItem(1, pair.inverse());

        MixedBootsPair explicitResistance = explicitResistancePair(level);
        chest.setItem(2, explicitResistance.primary());
        chest.setItem(3, explicitResistance.inverse());
        MixedBootsPair namedResistance = GameTestStacks.pair(Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS);
        chest.setItem(4, namedResistance.primary());
        chest.setItem(5, namedResistance.inverse());
        chest.setChanged();
    }

    private static MixedBootsPair persistedPair(String opaqueValue) {
        ItemStack owner = new ItemStack(TestContent.requireComponentBoots());
        owner.set(TestContent.requireOpaqueComponent(), opaqueValue);
        return GameTestStacks.pair(owner, new ItemStack(Items.DIAMOND_BOOTS));
    }

    private static MixedBootsPair restartPair(String opaqueValue) {
        ItemStack first = restartSource(TestContent.requireComponentBoots(), 2, 40, 2,
                                        opaqueValue, "first", "first-only");
        ItemStack second = restartSource(Items.IRON_BOOTS, 6, 80, 7,
                                         opaqueValue, "added by recipe", "second-only");
        first.set(DataComponents.ATTRIBUTE_MODIFIERS, movementModifiers(2.0D));
        second.set(DataComponents.ATTRIBUTE_MODIFIERS, movementModifiers(4.0D));
        return GameTestStacks.pair(first, second);
    }

    private static ItemStack restartSource(
            Item item,
            int numeric,
            int damage,
            int repairCost,
            String opaque,
            String added,
            String removed) {
        ItemStack source = new ItemStack(item);
        source.setDamageValue(damage);
        source.set(DataComponents.REPAIR_COST, repairCost);
        source.set(TestContent.requireNumericComponent(), numeric);
        source.set(TestContent.requireOpaqueComponent(), opaque);
        source.set(TestContent.ADDED_COMPONENT, added);
        source.set(TestContent.REMOVED_COMPONENT, removed);
        return source;
    }

    private static void preparePersistedRoot(ItemStack root, boolean primary, String token) {
        root.setDamageValue(primary ? PRIMARY_DAMAGE : INVERSE_DAMAGE);
        if (primary) {
            root.set(DataComponents.REPAIR_COST, PRIMARY_REPAIR_COST);
        } else {
            root.remove(DataComponents.REPAIR_COST);
        }
        root.set(DataComponents.CUSTOM_NAME,
                 Component.literal((primary ? "primary-" : "inverse-") + token));
        root.set(DataComponents.LORE, liveLore(primary, token));
    }

    private static void assertProjectedRestartComponents(ItemStack root, String token, String description) {
        if (!Integer.valueOf(4).equals(root.get(TestContent.requireNumericComponent()))
                || !token.equals(root.get(TestContent.requireOpaqueComponent()))
                || root.has(TestContent.ADDED_COMPONENT)
                || root.has(TestContent.REMOVED_COMPONENT)) {
            throw new AssertionError(description + " did not use the write-process component projections");
        }
    }

    private static ItemLore liveLore(boolean primary, String token) {
        return new ItemLore(List.of(Component.literal((primary ? "primary-lore-" : "inverse-lore-") + token)));
    }

    private static MixedBootsPair explicitResistancePair(ServerLevel level) {
        var damageTypes = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var fire = damageTypes.getOrThrow(net.minecraft.world.damagesource.DamageTypes.IN_FIRE);
        var cactus = damageTypes.getOrThrow(net.minecraft.world.damagesource.DamageTypes.CACTUS);
        ItemStack first = new ItemStack(Items.IRON_BOOTS);
        ItemStack second = new ItemStack(Items.GOLDEN_BOOTS);
        first.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(HolderSet.direct(fire, cactus)));
        second.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(HolderSet.direct(cactus)));
        return GameTestStacks.pair(first, second);
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

    private static void assertRestartedRoot(
            GameTestHelper helper, ItemStack stack, boolean primary, String token) {
        GameTestStacks.assertOrientation(helper, stack,
                                         primary ? TestContent.requireComponentBoots() : Items.IRON_BOOTS,
                                         primary ? Items.IRON_BOOTS : TestContent.requireComponentBoots(),
                                         primary ? "restarted primary" : "restarted inverse");
        int expectedMaximum = (new ItemStack(TestContent.requireComponentBoots()).getMaxDamage()
                + new ItemStack(Items.IRON_BOOTS).getMaxDamage()) / 2;
        helper.assertValueEqual(stack.getMaxDamage(), expectedMaximum, "persisted average maximum");
        helper.assertValueEqual(stack.getDamageValue(), primary ? PRIMARY_DAMAGE : INVERSE_DAMAGE,
                                "persisted explicit root damage");
        helper.assertValueEqual(stack.getOrDefault(DataComponents.REPAIR_COST, 0),
                                primary ? PRIMARY_REPAIR_COST : 0, "persisted edited repair cost");
        helper.assertValueEqual(stack.get(TestContent.requireNumericComponent()), 4,
                                "persisted projected numeric value");
        helper.assertValueEqual(stack.get(TestContent.requireOpaqueComponent()), token,
                                "persisted shared opaque value");
        helper.assertTrue(!stack.has(TestContent.ADDED_COMPONENT),
                          "Persisted root regained a deliberately absent projected component");
        helper.assertTrue(!stack.has(TestContent.REMOVED_COMPONENT),
                          "Persisted root regained an omitted component");
        helper.assertValueEqual(stack.get(DataComponents.CUSTOM_NAME),
                                Component.literal((primary ? "primary-" : "inverse-") + token),
                                "persisted custom name");
        helper.assertValueEqual(stack.get(DataComponents.LORE), liveLore(primary, token),
                                "persisted independently edited lore");
        helper.assertTrue(!stack.has(DataComponents.DAMAGE_RESISTANT),
                          "Persisted ordinary sources gained damage resistance");
        assertMovementAmount(stack, 3.0D, "stored result after rule change");

        MixedBootsData data = GameTestStacks.data(stack);
        ItemStack physicalFirst = primary ? data.left().source() : data.right().source();
        ItemStack physicalSecond = primary ? data.right().source() : data.left().source();
        assertRestartSource(helper, physicalFirst, TestContent.requireComponentBoots(),
                            2, 40, 2, token, "first", "first-only");
        assertRestartSource(helper, physicalSecond, Items.IRON_BOOTS,
                            6, 80, 7, token, "added by recipe", "second-only");
    }

    private static void assertRestartSource(
            GameTestHelper helper,
            ItemStack source,
            Item expectedItem,
            int numeric,
            int damage,
            int repairCost,
            String opaque,
            String added,
            String removed) {
        helper.assertTrue(source.is(expectedItem), "Persisted source has the wrong item identity");
        helper.assertValueEqual(source.get(TestContent.requireNumericComponent()), numeric,
                                "persisted source numeric component");
        helper.assertValueEqual(source.getDamageValue(), damage, "persisted source damage");
        helper.assertValueEqual(source.getOrDefault(DataComponents.REPAIR_COST, 0), repairCost,
                                "persisted source repair cost");
        helper.assertValueEqual(source.get(TestContent.requireOpaqueComponent()), opaque,
                                "persisted source opaque component");
        helper.assertValueEqual(source.get(TestContent.ADDED_COMPONENT), added,
                                "persisted source added component");
        helper.assertValueEqual(source.get(TestContent.REMOVED_COMPONENT), removed,
                                "persisted source omitted component");
        helper.assertTrue(!source.has(DataComponents.DAMAGE_RESISTANT),
                          "Persisted ordinary source gained damage resistance");
    }

    private static void assertSmithingAfterRuleChange(
            GameTestHelper helper, ItemStack root, boolean primary, String token) {
        ItemStack before = root.copy();
        var player = GameTestPlayers.makeMockServerPlayer(helper, GameType.CREATIVE);
        SmithingMenu menu = new SmithingMenu(1, player.getInventory(),
                                             ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.PAPER));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.QUARTZ));
        ItemStack result = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();

        helper.assertTrue(!result.isEmpty(), "Persisted mixed boots produced no loaded smithing result");
        helper.assertTrue(ItemStack.matches(root, before), "Smithing preview mutated the persisted root");
        MixedBootsData resultData = GameTestStacks.data(result);
        helper.assertTrue(resultData.left().source().is(primary
                                                        ? TestContent.UPGRADED_COMPONENT_BOOTS : Items.DIAMOND_BOOTS)
                                  && resultData.right().source().is(primary
                                                                    ? Items.DIAMOND_BOOTS : TestContent.UPGRADED_COMPONENT_BOOTS),
                          "Smithing did not transform both persisted sources in orientation order");
        helper.assertValueEqual(result.getMaxDamage(), resultData.left().source().getMaxDamage(),
                                "separate durability after restart");
        helper.assertValueEqual(result.getDamageValue(), primary ? PRIMARY_DAMAGE : INVERSE_DAMAGE,
                                "preserved raw damage after restart");
        helper.assertValueEqual(result.getOrDefault(DataComponents.REPAIR_COST, 0),
                                primary ? PRIMARY_REPAIR_COST : 0, "preserved repair cost after restart");
        helper.assertValueEqual(result.get(TestContent.requireNumericComponent()), 6,
                                "updated matching numeric projection");
        helper.assertTrue(!result.has(TestContent.requireOpaqueComponent()),
                          "Matching old projection was not removed when the new sources diverged");
        helper.assertValueEqual(result.get(TestContent.ADDED_COMPONENT), "added by recipe",
                                "Absent old projection was not added from the new sources");
        helper.assertTrue(!result.has(TestContent.REMOVED_COMPONENT),
                          "Omitted component appeared after recomposition");
        helper.assertTrue(!result.has(DataComponents.DAMAGE_RESISTANT),
                          "Smithing introduced unexpected damage resistance");
        helper.assertValueEqual(result.get(DataComponents.CUSTOM_NAME),
                                Component.literal((primary ? "primary-" : "inverse-") + token),
                                "custom name after recomposition");
        helper.assertValueEqual(result.get(DataComponents.LORE), liveLore(primary, token),
                                "independently edited lore after recomposition");
        assertMovementAmount(result, 6.0D, "recomposed attribute after rule change");
    }

    private static void assertExplicitResistancePair(
            GameTestHelper helper, ItemStack stack, boolean primary) {
        GameTestStacks.assertOrientation(helper, stack,
                                         primary ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS,
                                         primary ? Items.GOLDEN_BOOTS : Items.IRON_BOOTS,
                                         primary ? "explicit primary" : "explicit inverse");
        DamageResistant resistance = stack.get(DataComponents.DAMAGE_RESISTANT);
        helper.assertTrue(resistance != null && resistance.types().unwrapKey().isEmpty(),
                          "Explicit resistance became a named tag after restart");
        helper.assertTrue(stack.canBeHurtBy(helper.getLevel().damageSources().inFire()),
                          "Explicit intersection retained fire resistance");
        helper.assertTrue(!stack.canBeHurtBy(helper.getLevel().damageSources().cactus()),
                          "Explicit intersection lost cactus resistance");
        MixedBootsData data = GameTestStacks.data(stack);
        helper.assertTrue(!data.left().source().canBeHurtBy(helper.getLevel().damageSources().cactus())
                                  && !data.right().source().canBeHurtBy(helper.getLevel().damageSources().cactus()),
                          "Explicit resistance source snapshots changed after restart");
    }

    private static void assertNamedResistance(GameTestHelper helper, ItemStack stack, String description) {
        DamageResistant resistance = stack.get(DataComponents.DAMAGE_RESISTANT);
        helper.assertTrue(resistance != null && resistance.types().unwrapKey().isPresent(),
                          description + " did not retain its named tag");
        helper.assertTrue(!stack.canBeHurtBy(helper.getLevel().damageSources().inFire()),
                          description + " lost in-fire behavior");
        helper.assertTrue(stack.canBeHurtBy(helper.getLevel().damageSources().cactus()),
                          description + " gained unrelated cactus behavior");
    }

    private static void assertNamedResistancePair(
            GameTestHelper helper, ItemStack stack, String description) {
        GameTestStacks.assertOrientation(
                helper, stack, Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS, description);
        assertNamedResistance(helper, stack, description + " root");
        MixedBootsData data = GameTestStacks.data(stack);
        assertNamedResistance(helper, data.left().source(), description + " left source");
        assertNamedResistance(helper, data.right().source(), description + " right source");
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
