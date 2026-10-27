package games.alejandrocoria.mismatchedthings.api;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchContributors;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchContractTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Plain JVM tests have no world-data load to bind item defaults in 26.1.
        for (var item : new net.minecraft.world.item.Item[]{Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.APPLE,
                Items.DIRT, Items.DIAMOND, Items.PAPER}) {
            var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64);
            if (item == Items.IRON_BOOTS || item == Items.GOLDEN_BOOTS) {
                components.set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.MAX_DAMAGE, 200);
            }
            item.builtInRegistryHolder().bindComponents(components.build());
        }
    }

    @Test
    void resultsPreserveOrderedSnapshotsWithoutSharingMutableStacks() {
        ItemStack primary = new ItemStack(Items.IRON_BOOTS);
        primary.setDamageValue(12);
        ItemStack inverse = new ItemStack(Items.GOLDEN_BOOTS);
        inverse.set(DataComponents.CUSTOM_NAME, Component.literal("Inverse"));
        WorkbenchResult result = new WorkbenchResult(primary, inverse);

        primary.setDamageValue(24);
        inverse.remove(DataComponents.CUSTOM_NAME);
        result.primary().shrink(1);
        result.inverse().remove(DataComponents.CUSTOM_NAME);

        assertTrue(result.isComplete());
        assertTrue(result.primary().is(Items.IRON_BOOTS));
        assertEquals(12, result.primary().getDamageValue());
        assertEquals("Inverse", result.inverse().getHoverName().getString());
    }

    @Test
    void aMissingHalfDoesNotFormACompleteResult() {
        assertFalse(WorkbenchResult.EMPTY.isComplete());
        assertFalse(new WorkbenchResult(new ItemStack(Items.IRON_BOOTS), ItemStack.EMPTY).isComplete());
        assertFalse(new WorkbenchResult(ItemStack.EMPTY, new ItemStack(Items.GOLDEN_BOOTS)).isComplete());
    }

    @Test
    void duplicateOwnerDoesNotReplaceAnExistingAdmissionRule() {
        Identifier id = Identifier.parse("contract:duplicate");
        MismatchedWorkbench.registerContributor(id, stack -> stack.is(Items.APPLE));

        assertThrows(IllegalStateException.class,
                     () -> MismatchedWorkbench.registerContributor(id, stack -> stack.is(Items.DIRT)));
        assertTrue(WorkbenchContributors.accepts(new ItemStack(Items.APPLE)));
        assertFalse(WorkbenchContributors.accepts(new ItemStack(Items.DIRT)));
    }

    @Test
    void admissionReceivesOneDefensiveItemAndDoesNotMutateInventory() {
        AtomicInteger seenCount = new AtomicInteger();
        MismatchedWorkbench.registerContributor(Identifier.parse("contract:copy"), stack -> {
            if (!stack.is(Items.DIAMOND)) {
                return false;
            }
            seenCount.set(stack.getCount());
            stack.shrink(1);
            return true;
        });
        ItemStack inventory = new ItemStack(Items.DIAMOND, 8);

        assertTrue(WorkbenchContributors.accepts(inventory));
        assertEquals(1, seenCount.get());
        assertEquals(8, inventory.getCount());
        assertFalse(WorkbenchContributors.accepts(ItemStack.EMPTY));
    }

    @Test
    void parallelConsumerInitializationRetainsEveryAdmissionRule() throws Exception {
        var registrations = new ArrayList<Callable<Void>>();
        for (int index = 0; index < 16; index++) {
            String name = "parallel-" + index;
            registrations.add(() -> {
                MismatchedWorkbench.registerContributor(Identifier.parse("contract:" + name),
                        stack -> stack.is(Items.PAPER) && stack.getHoverName().getString().equals(name));
                return null;
            });
        }

        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var registration : executor.invokeAll(registrations)) {
                registration.get();
            }
        }

        for (int index = 0; index < 16; index++) {
            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("parallel-" + index));
            assertTrue(WorkbenchContributors.accepts(input), "Lost contributor " + index);
        }
    }
}
