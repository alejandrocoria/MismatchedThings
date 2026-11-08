package games.alejandrocoria.mismatchedthings.internal.compatibility.undergarden;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RogdoriumInfusionRulesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record NativeValue(int amount, int maximum) {
        private static final Codec<NativeValue> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("infusionAmount").forGetter(NativeValue::amount),
                Codec.INT.fieldOf("infusionMax").forGetter(NativeValue::maximum)).apply(instance, NativeValue::new));
    }

    private static final DataComponentType<Object> COMPONENT = component(NativeValue.CODEC);
    private static final RogdoriumInfusionRules.Binding API = RogdoriumInfusionRules.bind(COMPONENT);

    @Test
    void creationConservesReserveAndRetainsCapacityInEitherOrientation() {
        record Case(NativeValue first, NativeValue second, NativeValue result) {}
        var cases = List.of(
                new Case(null, null, null),
                new Case(new NativeValue(0, 3600), null, new NativeValue(0, 3600)),
                new Case(new NativeValue(1201, 3600), null, new NativeValue(600, 3600)),
                new Case(new NativeValue(1201, 3600), new NativeValue(300, 3600), new NativeValue(750, 3600)),
                new Case(new NativeValue(1201, 3600), new NativeValue(1201, 3600), new NativeValue(1201, 3600)),
                new Case(new NativeValue(100, 900), new NativeValue(400, 7200), new NativeValue(250, 7200)),
                new Case(new NativeValue(100, 900), null, new NativeValue(50, 3600)),
                new Case(new NativeValue(Integer.MAX_VALUE, Integer.MAX_VALUE), new NativeValue(Integer.MAX_VALUE, Integer.MAX_VALUE),
                        new NativeValue(Integer.MAX_VALUE, Integer.MAX_VALUE)),
                new Case(new NativeValue(Integer.MAX_VALUE, Integer.MAX_VALUE), null, new NativeValue(Integer.MAX_VALUE / 2, Integer.MAX_VALUE)));
        for (var example : cases) {
            var first = source(example.first);
            var second = source(example.second);
            var firstBefore = first.copy();
            var secondBefore = second.copy();
            for (boolean reverse : List.of(false, true)) {
                var output = source(new NativeValue(99, 100));
                output.set(DataComponents.CUSTOM_NAME, Component.literal("unrelated root state"));
                RogdoriumInfusionRules.initializeCreation(output, reverse ? second : first, reverse ? first : second, API);
                assertEquals(example.result, output.get(COMPONENT));
                assertEquals(Component.literal("unrelated root state"), output.get(DataComponents.CUSTOM_NAME));
            }
            assertTrue(ItemStack.matches(first, firstBefore));
            assertTrue(ItemStack.matches(second, secondBefore));
        }
    }

    @Test
    void invalidReserveOrCapacityRefusesBeforeMutatingAnyOutput() {
        for (var invalid : List.of(new NativeValue(-1, 3600), new NativeValue(3601, 3600),
                new NativeValue(0, 0), new NativeValue(0, -1))) {
            var bad = source(invalid);
            var good = source(new NativeValue(5, 3600));
            var inputBefore = bad.copy();
            for (boolean reverse : List.of(false, true)) {
                var output = source(new NativeValue(3, 7200));
                var before = output.copy();
                assertThrows(IllegalArgumentException.class, () -> RogdoriumInfusionRules.initializeCreation(
                        output, reverse ? good : bad, reverse ? bad : good, API));
                assertTrue(ItemStack.matches(before, output));
            }
            assertTrue(ItemStack.matches(inputBefore, bad));
        }
    }

    @Test
    void bindingRejectsCodecsThatDiscardOneOfTheNativeFields() {
        var lossy = NativeValue.CODEC.xmap(value -> new NativeValue(value.amount, 3600), value -> value);
        assertThrows(IllegalArgumentException.class, () -> RogdoriumInfusionRules.bind(component(lossy)));
        var wrongShape = DataComponentType.<Object>builder().persistent(Codec.INT.xmap(value -> value, value -> (Integer) value)).build();
        assertThrows(RuntimeException.class, () -> RogdoriumInfusionRules.bind(wrongShape));
    }

    @Test
    void outputSpecificCodecLossCannotPublishAPartialResult() {
        var codec = NativeValue.CODEC.xmap(value -> value.amount == 600 ? new NativeValue(0, value.maximum) : value, value -> value);
        var component = component(codec);
        var binding = RogdoriumInfusionRules.bind(component);
        var first = source(null);
        first.set(component, new NativeValue(1201, 3600));
        var output = source(null);
        output.set(component, new NativeValue(4, 3600));
        var before = output.copy();
        assertThrows(IllegalArgumentException.class, () -> RogdoriumInfusionRules.initializeCreation(output, first, source(null), binding));
        assertTrue(ItemStack.matches(output, before));
    }

    @Test
    void preservationKeepsExactPresenceIncludingZeroAndOldProjectionEquality() {
        for (var value : new NativeValue[]{null, new NativeValue(0, 3600), new NativeValue(1201, 3600), new NativeValue(9, 7200)}) {
            var previous = source(value);
            var before = previous.copy();
            var output = source(new NativeValue(1800, 3600));
            output.set(DataComponents.REPAIR_COST, 17);
            RogdoriumInfusionRules.preserveState(previous, output, COMPONENT);
            assertEquals(value, output.get(COMPONENT));
            assertEquals(value != null, output.has(COMPONENT));
            assertEquals(17, output.get(DataComponents.REPAIR_COST));
            assertTrue(ItemStack.matches(previous, before));
        }
    }

    @Test
    void preservationDoesNotInterpretAnUnsupportedNativeValue() {
        var previous = source(null);
        Object opaque = "opaque newer record";
        previous.set(COMPONENT, opaque);
        var output = source(new NativeValue(12, 3600));
        RogdoriumInfusionRules.preserveState(previous, output, COMPONENT);
        assertSame(opaque, output.get(COMPONENT));
        assertSame(opaque, previous.get(COMPONENT));
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(Codec<NativeValue> codec) {
        return (DataComponentType<Object>) (DataComponentType<?>) DataComponentType.<NativeValue>builder().persistent(codec).build();
    }

    private static ItemStack source(NativeValue value) {
        // Unit bootstrap has no data-driven item components; packaged product tests cover real source validation.
        var stack = new ItemStack(Holder.direct(Items.IRON_BOOTS, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build()));
        if (value != null) {
            stack.set(COMPONENT, value);
        }
        return stack;
    }
}
