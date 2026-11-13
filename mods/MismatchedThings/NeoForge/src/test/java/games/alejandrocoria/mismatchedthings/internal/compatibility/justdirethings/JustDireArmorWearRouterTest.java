package games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings.JustDireArmorWear;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JustDireArmorWearRouterTest {
    @Test
    void permissionAndSlotOwnershipControlExactlyOnceDispatch() {
        var paid = new AtomicInteger();
        var ordinaryCalls = new AtomicInteger();
        Operation<Void> ordinary = arguments -> {
            ordinaryCalls.incrementAndGet();
            return null;
        };
        JustDireArmorWear.Handler owner = (root, amount, entity, slot, fallback, damage) -> {
            assertEquals(EquipmentSlot.FEET, slot);
            assertEquals(3, amount);
            assertEquals(12.5F, damage);
            assertSame(ordinary, fallback);
            paid.incrementAndGet();
        };
        JustDireArmorWear.register(EquipmentSlot.FEET, owner);
        assertThrows(IllegalStateException.class, () -> JustDireArmorWear.register(EquipmentSlot.FEET,
                (root, amount, entity, slot, fallback, damage) -> {
                    throw new AssertionError("Replaced owner");
                }));

        JustDireArmorWearRouter.route(false, null, 3, null, EquipmentSlot.FEET, ordinary, 12.5F);
        JustDireArmorWearRouter.route(true, null, 3, null, EquipmentSlot.HEAD, ordinary, 12.5F);
        assertEquals(0, paid.get());
        assertEquals(2, ordinaryCalls.get());

        JustDireArmorWearRouter.route(true, null, 3, null, EquipmentSlot.FEET, ordinary, 12.5F);
        assertEquals(1, paid.get());
        assertEquals(2, ordinaryCalls.get(), "A dispatched owner alone decides whether ordinary wear runs");
    }

    @Test
    void handlerFailureCannotRetryOrdinaryWearAfterPossibleCommit() {
        var ordinaryCalls = new AtomicInteger();
        var failure = new IllegalStateException("Failure after payment");
        JustDireArmorWear.register(EquipmentSlot.LEGS, (root, amount, entity, slot, ordinary, damage) -> {
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> JustDireArmorWearRouter.route(true, null, 2, null, EquipmentSlot.LEGS, arguments -> {
                    ordinaryCalls.incrementAndGet();
                    return null;
                }, 8F)));
        assertEquals(0, ordinaryCalls.get());
    }

    @Test
    void invalidRegistrationsCannotPublishAnOwner() {
        JustDireArmorWear.Handler owner = (root, amount, entity, slot, ordinary, damage) -> {
        };
        assertThrows(IllegalArgumentException.class, () -> JustDireArmorWear.register(null, owner));
        assertThrows(IllegalArgumentException.class, () -> JustDireArmorWear.register(EquipmentSlot.CHEST, null));
        assertThrows(IllegalArgumentException.class, () -> JustDireArmorWear.register(EquipmentSlot.MAINHAND, owner));
    }
}
