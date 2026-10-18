package games.alejandrocoria.mismatchedboots.compatibility.neovitae;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoVitaeInfusionCompatibilityTest {
    private enum Type { RAW, RUINA }

    private static final NeoVitaeInfusionCompatibility.Binding API = new NeoVitaeInfusionCompatibility.Binding(
            DataComponentType.<Double>builder().persistent(Codec.DOUBLE).build(),
            DataComponentType.<Double>builder().persistent(Codec.DOUBLE).build(),
            DataComponentType.<Object>builder().persistent(Codec.STRING.xmap(Type::valueOf, value -> ((Type) value).name())).build(),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build(), Type.RAW);

    @Test
    void creationTableAndSwappedOrientations() {
        record Case(double capacityA, double amountA, Type typeA, double capacityB, double amountB, Type typeB,
                    double capacity, double amountLeft, Type typeLeft, double amountRight, Type typeRight) {}
        var cases = List.of(
                new Case(0, 0, Type.RAW, 0, 0, Type.RAW, 0, 0, Type.RAW, 0, Type.RAW),
                new Case(100, 60, Type.RUINA, 0, 0, Type.RAW, 100, 30, Type.RUINA, 30, Type.RUINA),
                new Case(100, 60, Type.RAW, 200, 100, Type.RAW, 200, 80, Type.RAW, 80, Type.RAW),
                new Case(100, 60, Type.RAW, 200, 0, Type.RUINA, 200, 30, Type.RAW, 30, Type.RAW),
                new Case(100, 0, Type.RUINA, 200, 0, Type.RUINA, 200, 0, Type.RAW, 0, Type.RAW),
                new Case(100, 60, Type.RAW, 200, 90, Type.RUINA, 200, 60, Type.RAW, 90, Type.RUINA),
                new Case(1, .125, Type.RUINA, 2, .625, Type.RUINA, 2, .375, Type.RUINA, .375, Type.RUINA));
        for (Case example : cases) {
            ItemStack first = boots(example.capacityA, example.amountA, example.typeA);
            ItemStack second = boots(example.capacityB, example.amountB, example.typeB);
            ItemStack firstBefore = first.copy(), secondBefore = second.copy();
            for (int preview = 0; preview < 2; preview++) {
                assertTuple(create(first, second), example.capacity, example.amountLeft, example.typeLeft);
                assertTuple(create(second, first), example.capacity, example.amountRight, example.typeRight);
            }
            assertTrue(ItemStack.matches(firstBefore, first));
            assertTrue(ItemStack.matches(secondBefore, second));
        }
    }

    @Test
    void markerUnionUsesPresenceIncludingFalseAndClearsStaleProjection() {
        for (int markers = 0; markers < 4; markers++) {
            ItemStack first = boots(0, 0, Type.RAW), second = first.copy();
            if ((markers & 1) != 0) { first.set(API.bloodMending(), false); }
            if ((markers & 2) != 0) { second.set(API.bloodMending(), true); }
            for (ItemStack root : List.of(create(first, second), create(second, first))) {
                assertEquals(markers != 0, root.has(API.bloodMending()));
                if (markers != 0) { assertEquals(true, root.get(API.bloodMending())); }
            }
        }
    }

    @Test
    void nativeDefaultsAndOrphanChargeDoNotInventStorage() {
        ItemStack installed = boots(10, 0, Type.RUINA);
        installed.remove(API.amount()); installed.remove(API.type());
        ItemStack orphan = boots(0, 0, Type.RAW);
        orphan.set(API.amount(), 123D); orphan.set(API.type(), Type.RUINA);
        assertTuple(create(installed, orphan), 10, 0, Type.RAW);
        assertTuple(create(orphan, boots(0, 0, Type.RAW)), 0, 0, Type.RAW);
    }

    @Test
    void fractionalAndExtremeBalancesNeverMintOrOverflow() {
        for (double[] amounts : List.of(new double[]{.1, .2}, new double[]{Double.MAX_VALUE, Double.MAX_VALUE},
                new double[]{Double.MIN_VALUE, 0}, new double[]{Math.nextDown(1D), 1D})) {
            ItemStack root = create(boots(Double.MAX_VALUE, amounts[0], Type.RAW),
                    boots(Double.MAX_VALUE, amounts[1], Type.RAW));
            double balance = root.get(API.amount());
            BigDecimal half = new BigDecimal(amounts[0]).add(new BigDecimal(amounts[1])).divide(BigDecimal.valueOf(2));
            assertTrue(Double.isFinite(balance));
            assertTrue(new BigDecimal(balance).compareTo(half) <= 0, "Creation minted Spiritus");
            assertTrue(half.subtract(new BigDecimal(balance)).compareTo(new BigDecimal(Math.ulp(balance))) <= 0,
                    "Creation discarded more than representable precision");
        }
    }

    @Test
    void malformedParticipatingTuplesRejectBeforeAnyOutputWrite() {
        ItemStack valid = boots(100, 10, Type.RAW);
        for (double[] bad : List.of(new double[]{0, 0}, new double[]{-1, 0}, new double[]{Double.NaN, 0},
                new double[]{Double.POSITIVE_INFINITY, 0}, new double[]{100, -1}, new double[]{100, 101},
                new double[]{100, Double.NaN}, new double[]{100, Double.POSITIVE_INFINITY})) {
            ItemStack invalid = boots(100, 0, Type.RAW);
            invalid.set(API.capacity(), bad[0]); invalid.set(API.amount(), bad[1]);
            for (boolean reverse : List.of(false, true)) {
                ItemStack output = projectedOutput(), before = output.copy(), sourceBefore = invalid.copy();
                assertThrows(IllegalArgumentException.class, () -> NeoVitaeInfusionCompatibility.initializeCreation(
                        output, reverse ? valid : invalid, reverse ? invalid : valid, API));
                assertTrue(ItemStack.matches(before, output));
                assertTrue(ItemStack.matches(sourceBefore, invalid));
            }
            ItemStack output = projectedOutput(), before = output.copy();
            assertThrows(IllegalArgumentException.class,
                    () -> NeoVitaeInfusionCompatibility.preserveState(invalid, output, API));
            assertTrue(ItemStack.matches(before, output));
        }
        ItemStack invalidType = boots(100, 1, Type.RAW);
        invalidType.set(API.type(), "unknown");
        assertThrows(IllegalArgumentException.class, () -> create(invalidType, valid));
        assertThrows(IllegalArgumentException.class,
                () -> NeoVitaeInfusionCompatibility.preserveState(invalidType, projectedOutput(), API));
    }

    @Test
    void preservationRestoresExactTuplePresenceWithoutDefaultingOrReaveraging() {
        // Includes installed-but-defaulted, orphan fields and entirely absent storage.
        for (int fields = 0; fields < 8; fields++) {
            ItemStack previous = boots(4096, 12.5, Type.RUINA);
            if ((fields & 1) == 0) { previous.remove(API.capacity()); }
            if ((fields & 2) == 0) { previous.remove(API.amount()); }
            if ((fields & 4) == 0) { previous.remove(API.type()); }
            assertPreserved(previous);
        }
        for (double balance : List.of(0D, 7.25, 60D)) {
            assertPreserved(boots(100, balance, Type.RAW));
        }
    }

    @Test
    void preservationKeepsLiveMarkerValueAndAbsenceWithoutReimportingSources() {
        ItemStack previous = boots(100, 12.5, Type.RUINA);
        for (Boolean marker : new Boolean[]{null, false, true}) {
            if (marker == null) { previous.remove(API.bloodMending()); }
            else { previous.set(API.bloodMending(), marker); }
            assertPreserved(previous);
        }
    }

    private static void assertPreserved(ItemStack previous) {
        // An unowned projected field must not be restored wholesale from the previous root.
        previous.set(DataComponents.REPAIR_COST, 2);
        ItemStack output = projectedOutput(), before = previous.copy();
        output.set(DataComponents.REPAIR_COST, 11);
        NeoVitaeInfusionCompatibility.preserveState(previous, output, API);
        for (var field : List.of(API.capacity(), API.amount(), API.type(), API.bloodMending())) {
            assertEquals(previous.has(field), output.has(field));
            assertEquals(previous.get(field), output.get(field));
        }
        assertEquals(11, output.get(DataComponents.REPAIR_COST));
        assertEquals(Component.literal("unrelated state"), output.get(DataComponents.CUSTOM_NAME));
        assertTrue(ItemStack.matches(before, previous));
    }

    private static ItemStack boots(double capacity, double amount, Type type) {
        // Unit bootstrap lacks data-driven item components; real factory wiring has its own runtime gate.
        ItemStack stack = new ItemStack(Holder.direct(Items.IRON_BOOTS, DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build()).build()));
        if (capacity > 0) {
            stack.set(API.capacity(), capacity); stack.set(API.amount(), amount); stack.set(API.type(), type);
        }
        return stack;
    }

    private static ItemStack projectedOutput() {
        ItemStack output = boots(5, 5, Type.RUINA);
        output.set(API.bloodMending(), false);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("unrelated state"));
        return output;
    }

    private static ItemStack create(ItemStack owner, ItemStack other) {
        ItemStack output = projectedOutput();
        NeoVitaeInfusionCompatibility.initializeCreation(output, owner, other, API);
        assertEquals(Component.literal("unrelated state"), output.get(DataComponents.CUSTOM_NAME));
        return output;
    }

    private static void assertTuple(ItemStack root, double capacity, double amount, Type type) {
        if (capacity == 0) {
            assertFalse(root.has(API.capacity())); assertFalse(root.has(API.amount())); assertFalse(root.has(API.type()));
        } else {
            assertEquals(capacity, root.get(API.capacity()));
            assertEquals(amount, root.get(API.amount()));
            assertEquals(type, root.get(API.type()));
        }
    }
}
