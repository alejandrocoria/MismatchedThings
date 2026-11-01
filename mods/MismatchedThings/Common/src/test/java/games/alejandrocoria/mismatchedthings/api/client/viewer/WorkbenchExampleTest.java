package games.alejandrocoria.mismatchedthings.api.client.viewer;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkbenchExampleTest {
    @BeforeAll
    @SuppressWarnings("deprecation")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Plain JVM fixtures do not load the data-driven item defaults.
        Items.IRON_LEGGINGS.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.MAX_DAMAGE, 200).build());
    }

    @Test
    void viewerSnapshotsDoNotMutateProductInputsOrOtherViewers() {
        ItemStack input = new ItemStack(Items.IRON_LEGGINGS, 4);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        WorkbenchExample example = new WorkbenchExample(List.of(input), List.of(input), List.of(input), List.of(input));

        input.set(DataComponents.CUSTOM_NAME, Component.literal("changed source"));
        example.primaryOutputs().getFirst().set(DataComponents.CUSTOM_NAME, Component.literal("changed viewer"));

        for (List<ItemStack> slot : List.of(example.leftInputs(), example.rightInputs(), example.primaryOutputs(), example.inverseOutputs())) {
            assertEquals("original", slot.getFirst().getHoverName().getString());
            assertEquals(1, slot.getFirst().getCount());
        }
        assertEquals(4, input.getCount());
    }

    @Test
    void rejectsRowsThatWouldBreakTheLinkedCycle() {
        ItemStack stack = new ItemStack(Items.IRON_LEGGINGS);
        List<ItemStack> one = List.of(stack);
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchExample(one, one, List.of(stack, stack), one));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchExample(one, one, List.of(ItemStack.EMPTY), one));
    }
}
