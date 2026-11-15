package games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings.JustDireRefunds;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JustDireRefundRouterTest {
    @BeforeAll
    @SuppressWarnings("deprecation")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Plain unit launches do not load Minecraft's data-driven item defaults.
        for (var item : new net.minecraft.world.item.Item[]{Items.IRON_BOOTS, Items.GOLDEN_BOOTS,
                Items.DIAMOND_BOOTS, Items.LEATHER_BOOTS}) {
            item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
        }
    }

    @Test
    void exactItemOwnershipAndReadinessControlDispatchWithoutLosingTheOperation() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var calls = new AtomicInteger();
        var root = new ItemStack(Items.IRON_BOOTS);
        Operation<Void> ordinary = arguments -> {
            assertSame(root, arguments[0]);
            assertEquals(1, arguments[1]);
            calls.incrementAndGet();
            return null;
        };
        JustDireRefunds.register(Items.IRON_BOOTS, (stack, amount, owner, operation) -> {
            assertSame(root, stack);
            assertSame(ordinary, operation);
            first.incrementAndGet();
            operation.call(stack, amount);
        });
        JustDireRefunds.register(Items.GOLDEN_BOOTS, (stack, amount, owner, operation) -> second.incrementAndGet());
        assertThrows(IllegalStateException.class, () -> JustDireRefunds.register(Items.IRON_BOOTS,
                (stack, amount, owner, operation) -> { throw new AssertionError("Replaced item owner"); }));

        JustDireRefundRouter.route(false, root, 1, null, ordinary);
        assertEquals(0, first.get());
        JustDireRefundRouter.route(true, root, 1, null, ordinary);
        assertEquals(1, first.get());
        assertEquals(2, calls.get());
        JustDireRefundRouter.route(true, new ItemStack(Items.GOLDEN_BOOTS), 1, null, arguments -> {
            throw new AssertionError("Dispatcher retried ordinary consumption after claimed callback");
        });
        assertEquals(1, second.get());
        JustDireRefundRouter.route(true, new ItemStack(Items.DIAMOND_BOOTS), 1, null, arguments -> { calls.incrementAndGet(); return null; });
        assertEquals(3, calls.get());
    }

    @Test
    void callbackFailureAfterConsumptionIsNotRetried() {
        var calls = new AtomicInteger();
        var failure = new IllegalStateException("Failure after committed consumption");
        JustDireRefunds.register(Items.LEATHER_BOOTS, (root, count, owner, ordinary) -> {
            ordinary.call(root, count);
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> JustDireRefundRouter.route(true,
                new ItemStack(Items.LEATHER_BOOTS), 1, null, arguments -> { calls.incrementAndGet(); return null; })));
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> JustDireRefunds.register(null, (root, count, owner, ordinary) -> {}));
        assertThrows(IllegalArgumentException.class, () -> JustDireRefunds.register(Items.CHAINMAIL_BOOTS, null));
    }
}
