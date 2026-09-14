package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class CrafterGameTests {
    private static final BlockPos CRAFTER = new BlockPos(0, 0, 0);
    private static final BlockPos OUTPUT = new BlockPos(0, 0, 1);
    private static final BlockPos POWER = new BlockPos(1, 0, 0);

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterInsertsBothOrientationsIntoInventory(GameTestHelper helper) {
        CrafterBlockEntity crafter = prepareCrafter(helper, false);
        helper.setBlock(OUTPUT, Blocks.BARREL);

        helper.tickBlock(CRAFTER);

        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));
        List<ItemStack> outputs = containerStacks(barrel);
        assertTwoOrientations(helper, outputs, "barrel");
        helper.assertTrue(crafter.isEmpty(), "Crafter inputs were not consumed");
        helper.assertTrue(nearbyItemEntities(helper).isEmpty(),
                "Crafter inserted its outputs but also dispensed unexpected items");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterDispensesBothOrientationsWithoutInventory(GameTestHelper helper) {
        CrafterBlockEntity crafter = prepareCrafter(helper, false);

        helper.tickBlock(CRAFTER);

        assertTwoOrientations(helper, nearbyItems(helper), "world");
        helper.assertTrue(crafter.isEmpty(), "Crafter inputs were not consumed");
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void blockedInventoryFallsBackWithoutLosingEitherOutput(GameTestHelper helper) {
        CrafterBlockEntity crafter = prepareCrafter(helper, false);
        helper.setBlock(OUTPUT, Blocks.BARREL);
        Container barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(OUTPUT));
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        helper.tickBlock(CRAFTER);

        assertTwoOrientations(helper, nearbyItems(helper), "blocked-output fallback");
        helper.assertTrue(crafter.isEmpty(), "Blocked-output Crafter inputs were not consumed");
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            helper.assertTrue(barrel.getItem(slot).is(Items.STONE) && barrel.getItem(slot).getCount() == 64,
                    "Blocked output inventory changed at slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void crafterAcceptsRepresentativeLayoutCategories(GameTestHelper helper) {
        CrafterBlockEntity crafter = prepareCrafter(helper, false);
        int[][] layouts = {{0, 1}, {0, 3}, {0, 4}, {0, 8}, {2, 6}};

        for (int[] layout : layouts) {
            crafter.clearContent();
            crafter.setItem(layout[0], GameTestStacks.ownerDivergentBoots(Items.IRON_BOOTS, 2.0D));
            crafter.setItem(layout[1], GameTestStacks.ownerDivergentBoots(Items.GOLDEN_BOOTS, 6.0D));
            helper.tickBlock(CRAFTER);

            List<ItemEntity> entities = nearbyItemEntities(helper);
            assertTwoOrientations(helper, entities.stream().map(ItemEntity::getItem).toList(),
                    "Crafter layout " + layout[0] + "/" + layout[1]);
            helper.assertTrue(crafter.isEmpty(),
                    "Crafter layout " + layout[0] + "/" + layout[1] + " retained an input");
            entities.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(structure = GameTestStacks.EMPTY_STRUCTURE)
    public void repeatedCyclesAndDisabledSlotsRemainConservative(GameTestHelper helper) {
        CrafterBlockEntity crafter = prepareCrafter(helper, true);
        helper.pulseRedstone(POWER, 1);
        helper.runAfterDelay(6, () -> {
            assertTwoOrientations(helper, nearbyItems(helper), "first redstone pulse");
            helper.assertTrue(crafter.isEmpty(), "First redstone pulse retained an input");
            assertUnusedSlotsRemainDisabled(helper, crafter);
            crafter.setItem(0, GameTestStacks.ownerDivergentBoots(Items.IRON_BOOTS, 2.0D));
            crafter.setItem(8, GameTestStacks.ownerDivergentBoots(Items.GOLDEN_BOOTS, 6.0D));
            helper.pulseRedstone(POWER, 1);
        });
        helper.runAfterDelay(12, () -> {
            List<ItemStack> bothCycles = nearbyItems(helper);
            helper.assertValueEqual(bothCycles.size(), 4, "mixed outputs after two redstone pulses");
            long primary = bothCycles.stream()
                    .filter(stack -> isOrientation(stack, Items.IRON_BOOTS, Items.GOLDEN_BOOTS)).count();
            long inverse = bothCycles.stream()
                    .filter(stack -> isOrientation(stack, Items.GOLDEN_BOOTS, Items.IRON_BOOTS)).count();
            helper.assertValueEqual(primary, 2L, "primary outputs after two redstone pulses");
            helper.assertValueEqual(inverse, 2L, "inverse outputs after two redstone pulses");
            helper.assertTrue(crafter.isEmpty(), "Second redstone pulse retained an input");
            assertUnusedSlotsRemainDisabled(helper, crafter);
            helper.succeed();
        });
    }

    private static CrafterBlockEntity prepareCrafter(GameTestHelper helper, boolean disableUnusedSlots) {
        helper.setBlock(CRAFTER, Blocks.CRAFTER.defaultBlockState()
                .setValue(BlockStateProperties.ORIENTATION, FrontAndTop.SOUTH_UP));
        CrafterBlockEntity crafter = helper.getBlockEntity(CRAFTER, CrafterBlockEntity.class);
        crafter.setItem(0, GameTestStacks.ownerDivergentBoots(Items.IRON_BOOTS, 2.0D));
        crafter.setItem(8, GameTestStacks.ownerDivergentBoots(Items.GOLDEN_BOOTS, 6.0D));
        if (disableUnusedSlots) {
            for (int slot = 1; slot < 8; slot++) {
                crafter.setSlotState(slot, false);
            }
        }
        return crafter;
    }

    private static List<ItemStack> containerStacks(Container container) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                outputs.add(stack);
            }
        }
        return outputs;
    }

    private static List<ItemStack> nearbyItems(GameTestHelper helper) {
        return nearbyItemEntities(helper).stream().map(ItemEntity::getItem).toList();
    }

    private static List<ItemEntity> nearbyItemEntities(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(CRAFTER)).inflate(3.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive);
    }

    private static void assertTwoOrientations(GameTestHelper helper, List<ItemStack> outputs, String location) {
        helper.assertValueEqual(outputs.size(), 2, "mixed outputs in " + location);
        helper.assertTrue(outputs.stream().allMatch(stack -> stack.is(ModContent.MIXED_BOOTS_KEY)
                        && stack.getCount() == 1),
                "Unexpected item or stack count in " + location + ": " + outputs);
        helper.assertValueEqual(outputs.stream()
                .filter(stack -> isOrientation(stack, Items.IRON_BOOTS, Items.GOLDEN_BOOTS)).count(),
                1L, "primary outputs in " + location);
        helper.assertValueEqual(outputs.stream()
                .filter(stack -> isOrientation(stack, Items.GOLDEN_BOOTS, Items.IRON_BOOTS)).count(),
                1L, "inverse outputs in " + location);
        ItemStack primary = outputs.stream()
                .filter(stack -> isOrientation(stack, Items.IRON_BOOTS, Items.GOLDEN_BOOTS))
                .findFirst().orElseThrow();
        ItemStack inverse = outputs.stream()
                .filter(stack -> isOrientation(stack, Items.GOLDEN_BOOTS, Items.IRON_BOOTS))
                .findFirst().orElseThrow();
        GameTestStacks.assertOwnerDivergentAttribute(helper, primary, 2.0D, "Crafter primary in " + location);
        GameTestStacks.assertOwnerDivergentAttribute(helper, inverse, 6.0D, "Crafter inverse in " + location);
    }

    private static void assertUnusedSlotsRemainDisabled(GameTestHelper helper, CrafterBlockEntity crafter) {
        for (int slot = 1; slot < 8; slot++) {
            helper.assertTrue(crafter.isSlotDisabled(slot), "Crafter slot " + slot + " became enabled");
        }
    }

    private static boolean isOrientation(ItemStack stack, net.minecraft.world.item.Item left, net.minecraft.world.item.Item right) {
        var data = GameTestStacks.data(stack);
        return data.left().source().is(left) && data.right().source().is(right);
    }
}
