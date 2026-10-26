package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

public final class CrafterGameTests {
    private static final BlockPos CRAFTER = BlockPos.ZERO;
    private static final BlockPos OUTPUT = new BlockPos(0, 0, 1);
    private static final BlockPos POWER = new BlockPos(1, 0, 0);

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterCannotComposeBootsInRepresentativeLayouts(GameTestHelper helper) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                                               .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        helper.setBlock(OUTPUT, Blocks.BARREL);
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));
        int[][] layouts = {{0, 1}, {0, 3}, {0, 4}, {0, 8}, {2, 6}};

        for (int[] layout : layouts) {
            crafter.clearContent();
            crafter.setItem(layout[0], GameTestStacks.ownerDivergentBoots(Items.IRON_BOOTS, 2.0D));
            crafter.setItem(layout[1], GameTestStacks.ownerDivergentBoots(Items.GOLDEN_BOOTS, 6.0D));

            helper.tickBlock(CRAFTER);

            assertInputsUnchanged(helper, crafter, layout[0], layout[1],
                                  "Crafter layout " + layout[0] + "/" + layout[1]);
            helper.assertTrue(barrel.isEmpty(),
                              "Crafter layout " + layout[0] + "/" + layout[1] + " inserted an output");
            assertNoMixedDrops(helper, "Crafter layout " + layout[0] + "/" + layout[1]);
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void redstonePulseLeavesInputsAndDisabledSlotsUnchanged(GameTestHelper helper) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                                               .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        crafter.setItem(0, GameTestStacks.ownerDivergentBoots(Items.IRON_BOOTS, 2.0D));
        crafter.setItem(8, GameTestStacks.ownerDivergentBoots(Items.GOLDEN_BOOTS, 6.0D));
        for (int slot = 1; slot < 8; slot++) {
            crafter.setSlotState(slot, false);
        }

        helper.pulseRedstone(POWER, 1);
        helper.runAfterDelay(6, () -> {
            assertInputsUnchanged(helper, crafter, 0, 8, "Redstone-triggered Crafter");
            for (int slot = 1; slot < 8; slot++) {
                helper.assertTrue(crafter.isSlotDisabled(slot),
                                  "Crafter slot " + slot + " became enabled");
            }
            assertNoMixedDrops(helper, "Redstone-triggered Crafter");
            helper.pulseRedstone(POWER, 1);
            helper.runAfterDelay(6, () -> {
                assertInputsUnchanged(helper, crafter, 0, 8, "Repeated redstone-triggered Crafter");
                assertNoMixedDrops(helper, "Repeated redstone-triggered Crafter");
                helper.succeed();
            });
        });
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterRepairsAcrossRepeatedPulsesAndPreservesDisabledSlots(GameTestHelper helper) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                                               .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        helper.setBlock(OUTPUT, Blocks.BARREL);
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));
        for (int slot = 1; slot < 8; slot++) {
            crafter.setSlotState(slot, false);
        }

        ItemStack firstMixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        firstMixed.setDamageValue(firstMixed.getMaxDamage() - 5);
        ItemStack firstDonor = new ItemStack(Items.IRON_BOOTS);
        firstDonor.setDamageValue(firstDonor.getMaxDamage() - 3);
        int firstExpectedDamage = expectedDamage(firstMixed, firstDonor);
        var firstSources = GameTestStacks.data(firstMixed);
        crafter.setItem(0, firstMixed);
        crafter.setItem(8, firstDonor);

        helper.pulseRedstone(POWER, 1);
        helper.runAfterDelay(6, () -> {
            List<ItemStack> firstOutputs = mixedOutputs(barrel);
            helper.assertValueEqual(firstOutputs.size(), 1, "first Crafter repair output count");
            helper.assertValueEqual(firstOutputs.getFirst().getDamageValue(), firstExpectedDamage,
                                    "first Crafter repair damage");
            helper.assertTrue(GameTestStacks.data(firstOutputs.getFirst()).equals(firstSources),
                              "First Crafter repair changed source snapshots");
            assertCrafterConsumed(helper, crafter, "first repair pulse");
            assertDisabledSlots(helper, crafter);

            ItemStack secondDonor = new ItemStack(Items.GOLDEN_BOOTS);
            ItemStack secondMixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
            var secondSources = GameTestStacks.data(secondMixed);
            crafter.setItem(0, secondDonor);
            crafter.setItem(8, secondMixed);
            helper.pulseRedstone(POWER, 1);
            helper.runAfterDelay(6, () -> {
                List<ItemStack> outputs = mixedOutputs(barrel);
                helper.assertValueEqual(outputs.size(), 2, "repeated Crafter repair output count");
                ItemStack secondOutput = outputs.get(1);
                helper.assertValueEqual(secondOutput.getDamageValue(), 0, "saturated Crafter repair damage");
                helper.assertTrue(GameTestStacks.data(secondOutput).equals(secondSources),
                                  "Second Crafter repair changed source snapshots");
                assertCrafterConsumed(helper, crafter, "second repair pulse");
                assertDisabledSlots(helper, crafter);
                helper.assertTrue(helper.getLevel().getEntities(
                                          EntityType.EXPERIENCE_ORB, helper.getBounds(), Entity::isAlive).isEmpty(),
                                  "Crafter repair created experience");
                helper.succeed();
            });
        });
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void fullOutputContainerEjectsRepairWithoutLosingIt(GameTestHelper helper) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                                               .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        helper.setBlock(OUTPUT, Blocks.BARREL);
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        ItemStack mixed = GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        var sources = GameTestStacks.data(mixed);
        crafter.setItem(0, mixed);
        crafter.setItem(8, new ItemStack(Items.IRON_BOOTS));

        helper.pulseRedstone(POWER, 1);
        helper.runAfterDelay(6, () -> {
            assertCrafterConsumed(helper, crafter, "repair with full output container");
            helper.assertTrue(barrel.countItem(Items.STONE) == barrel.getContainerSize() * 64,
                              "Crafter changed a full output container");
            List<ItemStack> drops = helper.getLevel().getEntitiesOfClass(
                                                  ItemEntity.class, helper.getBounds().inflate(3.0), Entity::isAlive).stream()
                                          .map(ItemEntity::getItem)
                                          .filter(ModContent::isMixedBoots)
                                          .toList();
            helper.assertValueEqual(drops.size(), 1, "ejected Crafter repair count");
            helper.assertTrue(GameTestStacks.data(drops.getFirst()).equals(sources),
                              "Ejected Crafter repair changed source snapshots");
            helper.succeed();
        });
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterRejectsInvalidRepairInputsWithoutConsumption(GameTestHelper helper) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                                               .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        helper.setBlock(OUTPUT, Blocks.BARREL);
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));

        assertCrafterRejected(helper, crafter, barrel, List.of(
                new SlottedStack(0, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS)),
                new SlottedStack(8, new ItemStack(Items.DIAMOND_BOOTS))), "unrelated donor");
        assertCrafterRejected(helper, crafter, barrel, List.of(
                                      new SlottedStack(0, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS)),
                                      new SlottedStack(8, GameTestStacks.pair(Items.IRON_BOOTS, Items.GOLDEN_BOOTS).inverse())),
                              "Mixed/Mixed");
        assertCrafterRejected(helper, crafter, barrel, List.of(
                new SlottedStack(0, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID))),
                new SlottedStack(8, new ItemStack(Items.IRON_BOOTS))), "bare mixed stack");
        assertCrafterRejected(helper, crafter, barrel, List.of(
                new SlottedStack(0, GameTestStacks.mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS)),
                new SlottedStack(4, new ItemStack(Items.STONE)),
                new SlottedStack(8, new ItemStack(Items.IRON_BOOTS))), "extra input");
        helper.succeed();
    }

    private static void assertInputsUnchanged(
            GameTestHelper helper,
            CrafterBlockEntity crafter,
            int firstSlot,
            int secondSlot,
            String description) {
        helper.assertTrue(crafter.getItem(firstSlot).is(Items.IRON_BOOTS)
                                  && crafter.getItem(firstSlot).getCount() == 1,
                          description + " consumed the first input");
        helper.assertTrue(crafter.getItem(secondSlot).is(Items.GOLDEN_BOOTS)
                                  && crafter.getItem(secondSlot).getCount() == 1,
                          description + " consumed the second input");
        helper.assertValueEqual(crafter.countItem(Items.IRON_BOOTS), 1,
                                description + " iron boots count");
        helper.assertValueEqual(crafter.countItem(Items.GOLDEN_BOOTS), 1,
                                description + " golden boots count");
    }

    private static void assertNoMixedDrops(GameTestHelper helper, String description) {
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                                        ItemEntity.class, helper.getBounds().inflate(3.0), Entity::isAlive).stream()
                                .noneMatch(entity -> ModContent.isMixedBoots(entity.getItem())),
                          description + " dispensed mixed boots");
    }

    private static void assertCrafterRejected(
            GameTestHelper helper,
            CrafterBlockEntity crafter,
            Container output,
            List<SlottedStack> inputs,
            String description) {
        crafter.clearContent();
        output.clearContent();
        List<ItemStack> before = new ArrayList<>(inputs.size());
        for (SlottedStack input : inputs) {
            crafter.setItem(input.slot(), input.stack());
            before.add(input.stack().copy());
        }

        helper.tickBlock(CRAFTER);

        for (int index = 0; index < inputs.size(); index++) {
            SlottedStack input = inputs.get(index);
            helper.assertTrue(ItemStack.matches(before.get(index), crafter.getItem(input.slot())),
                              "Crafter " + description + " changed input " + input.slot());
        }
        helper.assertTrue(output.isEmpty(), "Crafter " + description + " inserted an output");
        assertNoMixedDrops(helper, "Crafter " + description);
    }

    private static void assertCrafterConsumed(
            GameTestHelper helper, CrafterBlockEntity crafter, String description) {
        helper.assertTrue(crafter.getItem(0).isEmpty() && crafter.getItem(8).isEmpty(),
                          "Crafter did not consume both inputs after " + description);
    }

    private static void assertDisabledSlots(GameTestHelper helper, CrafterBlockEntity crafter) {
        for (int slot = 1; slot < 8; slot++) {
            helper.assertTrue(crafter.isSlotDisabled(slot), "Crafter slot " + slot + " became enabled");
        }
    }

    private static List<ItemStack> mixedOutputs(Container container) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (ModContent.isMixedBoots(stack)) {
                outputs.add(stack);
            }
        }
        return outputs;
    }

    private static int expectedDamage(ItemStack mixed, ItemStack donor) {
        int maximum = mixed.getMaxDamage();
        int remaining = maximum - mixed.getDamageValue()
                + donor.getMaxDamage() - donor.getDamageValue()
                + maximum * 5 / 100;
        return Math.max(0, maximum - Math.min(maximum, remaining));
    }

    private record SlottedStack(int slot, ItemStack stack) {
    }
}
