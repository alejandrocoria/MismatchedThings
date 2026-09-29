package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
}
