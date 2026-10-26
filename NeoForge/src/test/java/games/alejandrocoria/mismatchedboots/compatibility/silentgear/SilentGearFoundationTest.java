package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.composition.AttributeComposer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearFoundationTest {
    private static final Identifier ID = Identifier.parse("silentgear:test_trait");
    private static final AttributeRuleSet AVERAGE = new AttributeRuleSet(AttributePolicy.AVERAGE, Map.of());

    @Test
    void exactDuplicatesComposeOnceButNonNativeSourcesAreNotNormalized() {
        var single = entry(2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.attributeModifiers());
        var duplicate = new ItemAttributeModifiers(List.of(single, single));
        var normalized = SilentGearCompatibility.normalize(duplicate);
        assertEquals(List.of(single), normalized.modifiers());
        var mixed = AttributeComposer.composePrimary(normalized, ItemAttributeModifiers.EMPTY, AVERAGE);
        assertEquals(1, mixed.modifiers().size());
        assertEquals(1, mixed.modifiers().getFirst().modifier().amount());
        // Unit bootstrap does not bind data-driven item defaults; this is still the real non-SG item.
        assertEquals(duplicate,
                     SilentGearCompatibility.normalize(new ItemStack(Holder.direct(Items.DIAMOND_BOOTS, DataComponentMap.EMPTY)), duplicate));
    }

    @Test
    void differingAmountOperationSlotOrDisplayRetainsAmbiguityFallback() {
        var original = entry(2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.attributeModifiers());
        for (var conflict : List.of(
                entry(3, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, original.display()),
                entry(2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, EquipmentSlotGroup.FEET, original.display()),
                entry(2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.ANY, original.display()),
                entry(2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.hidden()))) {
            var input = new ItemAttributeModifiers(List.of(original, original, conflict));
            var normalized = SilentGearCompatibility.normalize(input);
            assertEquals(List.of(original, conflict), normalized.modifiers());
            assertEquals(normalized, AttributeComposer.composePrimary(normalized, ItemAttributeModifiers.EMPTY, AVERAGE));
            assertTrue(AttributeComposer.composePrimary(ItemAttributeModifiers.EMPTY, normalized, AVERAGE).modifiers().isEmpty());
        }
    }

    @Test
    void codecsRejectInvalidLanesPoliciesAndOwnershipRatherThanGuessing() {
        var empty = new SilentGearState(Optional.empty(), Optional.empty(), List.of(), ItemAttributeModifiers.EMPTY, List.of());
        JsonObject base = SilentGearState.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow().getAsJsonObject();
        for (String phase : List.of("unknown", "polished")) {
            var malformed = base.deepCopy();
            var lane = new JsonObject();
            lane.addProperty("phase", phase);
            lane.addProperty("level", 1);
            lane.addProperty("stage", 0);
            lane.addProperty("counter", phase.equals("polished") ? -1 : 0);
            malformed.add("left", lane);
            assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
        }
        for (String policy : List.of("average", "unknown")) {
            var malformed = base.deepCopy();
            var rules = new JsonArray();
            var rule = new JsonObject();
            rule.addProperty("attribute", "minecraft:armor");
            rule.addProperty("policy", policy);
            rules.add(rule);
            if (policy.equals("average")) {
                rules.add(rule.deepCopy());
            }
            malformed.add("rules", rules);
            assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
        }

        var rule = new SilentGearState.Rule(Identifier.withDefaultNamespace("luck"), AttributePolicy.AVERAGE);
        var modifier = entry(1, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.attributeModifiers());
        assertThrows(IllegalArgumentException.class,
                     () -> new SilentGearState(Optional.empty(), Optional.empty(), List.of(rule, rule), ItemAttributeModifiers.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class,
                     () -> new SilentGearState(Optional.empty(),
                                               Optional.empty(),
                                               List.of(),
                                               new ItemAttributeModifiers(List.of(modifier)),
                                               List.of()));
        assertThrows(IllegalArgumentException.class,
                     () -> new SilentGearState(Optional.empty(),
                                               Optional.empty(),
                                               List.of(),
                                               ItemAttributeModifiers.EMPTY,
                                               List.of(SilentGearState.Key.of(modifier))));
        assertThrows(IllegalArgumentException.class, () -> new SilentGearState.Oxidation("oxidized", 1, 128, 0));
        assertThrows(IllegalArgumentException.class, () -> new SilentGearState.Oxidation("oxidized", 0, 1, 0));
        var badSample = base.deepCopy();
        var sample = new JsonObject();
        sample.addProperty("damage", 101);
        sample.addProperty("maximum", 100);
        sample.addProperty("definition", "");
        badSample.add("sample", sample);
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, badSample).error().isPresent());
    }

    @Test
    void livePhaseLevelAndWearSampleRoundTripIndependentlyOfNativeClasses() {
        var state = new SilentGearState(Optional.of(new SilentGearState.Oxidation("oxidized", 2, 1, 17)),
                                        Optional.empty(),
                                        List.of(),
                                        ItemAttributeModifiers.EMPTY,
                                        List.of(),
                                        Optional.of(new SilentGearState.Sample(50, 100)));
        var encoded = SilentGearState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, SilentGearState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var malformed = encoded.deepCopy().getAsJsonObject();
        malformed.getAsJsonObject("left").addProperty("level", 0);
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, malformed).error().isPresent());
    }

    private static ItemAttributeModifiers.Entry entry(double amount, AttributeModifier.Operation operation,
                                                      EquipmentSlotGroup slot, ItemAttributeModifiers.Display display) {
        return new ItemAttributeModifiers.Entry(Attributes.LUCK, new AttributeModifier(ID, amount, operation), slot, display);
    }
}
