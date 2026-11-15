package games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedthings.api.compatibility.mysticalagriculture.MysticalRefunds;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysticalRefundObserverTest {
    @BeforeAll
    @SuppressWarnings("deprecation")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Plain unit launches do not load Minecraft's data-driven item defaults.
        for (var item : new Item[]{Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS,
                Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.NETHERITE_BOOTS, Items.IRON_HELMET}) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
        }
    }

    @Test
    void readinessAndExactOwnerControlClaimsWithoutReplacingDuplicateRegistration() {
        var calls = new AtomicInteger();
        var root = new ItemStack(Items.IRON_BOOTS);
        var receipt = new RecordingReceipt();
        MysticalRefunds.register(Items.IRON_BOOTS, (stack, damage, owner) -> {
            assertSame(root, stack);
            assertEquals(37, damage); // The proposed damage value passes through unchanged.
            assertNull(owner);
            calls.incrementAndGet();
            return receipt;
        });
        assertThrows(IllegalStateException.class, () -> MysticalRefunds.register(Items.IRON_BOOTS,
                (stack, damage, owner) -> { throw new AssertionError("Replaced receipt owner"); }));
        assertThrows(IllegalArgumentException.class, () -> MysticalRefunds.register(null, (stack, damage, owner) -> null));
        assertThrows(IllegalArgumentException.class, () -> MysticalRefunds.register(Items.GOLDEN_BOOTS, null));

        assertNull(MysticalRefundObserver.capture(false, root, 37, null));
        assertNull(MysticalRefundObserver.capture(true, new ItemStack(Items.IRON_HELMET), 37, null));
        assertEquals(0, calls.get());
        var capture = MysticalRefundObserver.capture(true, root, 37, null);
        assertNotNull(capture);
        finish(capture);
        assertEquals(List.of("arm", "consumed", "complete"), receipt.calls);
        assertEquals(1, calls.get());
    }

    @Test
    void declinedItemDoesNotDisableAnotherOwner() {
        MysticalRefunds.register(Items.DIAMOND_BOOTS, (root, damage, owner) -> null);
        var receipt = new RecordingReceipt();
        MysticalRefunds.register(Items.LEATHER_BOOTS, (root, damage, owner) -> receipt);
        var declined = MysticalRefundObserver.capture(true, new ItemStack(Items.DIAMOND_BOOTS), 1, null);
        assertNull(declined);
        finish(declined);
        finish(MysticalRefundObserver.capture(true, new ItemStack(Items.LEATHER_BOOTS), 1, null));
        assertEquals(List.of("arm", "consumed", "complete"), receipt.calls);
    }

    @Test
    void incompleteAndRepeatedStagesCannotDeliverOrReenterTheSameAuthorization() {
        var receipt = new RecordingReceipt();
        MysticalRefunds.register(Items.CHAINMAIL_BOOTS, (root, damage, owner) -> receipt);
        var capture = MysticalRefundObserver.capture(true, new ItemStack(Items.CHAINMAIL_BOOTS), 1, null);
        receipt.action = () -> finish(capture);

        MysticalRefundObserver.consumed(capture);
        MysticalRefundObserver.complete(capture);
        assertEquals(List.of(), receipt.calls);
        MysticalRefundObserver.arm(capture);
        MysticalRefundObserver.arm(capture);
        MysticalRefundObserver.complete(capture);
        assertEquals(List.of("arm"), receipt.calls);
        MysticalRefundObserver.consumed(capture);
        MysticalRefundObserver.consumed(capture);
        MysticalRefundObserver.complete(capture);
        finish(capture);
        assertEquals(List.of("arm", "consumed", "complete"), receipt.calls);
    }

    @Test
    void throwingStageClosesOnlyThatCaptureAndCannotLeakIntoLaterInvocations() {
        var next = new RecordingReceipt[1];
        MysticalRefunds.register(Items.NETHERITE_BOOTS, (root, damage, owner) -> next[0]);
        for (String failedStage : List.of("arm", "consumed", "complete")) {
            var receipt = new RecordingReceipt();
            next[0] = receipt;
            var capture = MysticalRefundObserver.capture(true, new ItemStack(Items.NETHERITE_BOOTS), 1, null);
            var failure = new IllegalStateException(failedStage);
            receipt.action = () -> {
                if (receipt.calls.getLast().equals(failedStage)) {
                    throw failure;
                }
            };
            assertSame(failure, assertThrows(IllegalStateException.class, () -> finish(capture)));
            var delivered = List.copyOf(receipt.calls);
            finish(capture);
            assertEquals(delivered, receipt.calls);

            var later = new RecordingReceipt();
            next[0] = later;
            finish(MysticalRefundObserver.capture(true, new ItemStack(Items.NETHERITE_BOOTS), 1, null));
            assertEquals(List.of("arm", "consumed", "complete"), later.calls);
        }
    }

    @Test
    void anotherInvocationHasIndependentStagesEvenDuringCompletion() {
        var receipts = new ArrayList<RecordingReceipt>();
        MysticalRefunds.register(Items.GOLDEN_BOOTS, (root, damage, owner) -> {
            var receipt = new RecordingReceipt();
            receipts.add(receipt);
            return receipt;
        });
        var first = MysticalRefundObserver.capture(true, new ItemStack(Items.GOLDEN_BOOTS), 1, null);
        var second = MysticalRefundObserver.capture(true, new ItemStack(Items.GOLDEN_BOOTS), 1, null);
        receipts.getFirst().action = () -> {
            if (receipts.getFirst().calls.getLast().equals("complete")) {
                finish(second);
                MysticalRefundObserver.complete(first);
            }
        };
        finish(first);
        finish(second);
        assertEquals(2, receipts.size());
        for (var receipt : receipts) {
            assertEquals(List.of("arm", "consumed", "complete"), receipt.calls);
        }
    }

    private static void finish(MysticalRefundObserver.Capture capture) {
        MysticalRefundObserver.arm(capture);
        MysticalRefundObserver.consumed(capture);
        MysticalRefundObserver.complete(capture);
    }

    private static final class RecordingReceipt implements MysticalRefunds.Receipt {
        private final List<String> calls = new ArrayList<>();
        private Runnable action = () -> {};

        @Override
        public void arm() {
            calls.add("arm");
            action.run();
        }

        @Override
        public void consumed() {
            calls.add("consumed");
            action.run();
        }

        @Override
        public void complete() {
            calls.add("complete");
            action.run();
        }
    }
}
