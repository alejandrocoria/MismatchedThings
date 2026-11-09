package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedleggings.config.AttributeRulesConfig;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
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
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearFoundationTest {
    @Test
    void exactEntriesCollapseInOrderButConflictsRetainOrdinaryAmbiguity() {
        var original = entry(2, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.LEGS, ItemAttributeModifiers.Display.attributeModifiers());
        var duplicates = new ItemAttributeModifiers(List.of(original, original));
        var single = SilentGearCompatibility.normalize(duplicates);
        assertEquals(List.of(original), single.modifiers());
        assertEquals(1, AttributeMerger.merge(single, ItemAttributeModifiers.EMPTY, id -> AttributePolicy.AVERAGE)
                .primary().modifiers().getFirst().modifier().amount());
        assertEquals(duplicates, SilentGearCompatibility.normalize(
                new ItemStack(Holder.direct(Items.DIAMOND_LEGGINGS, DataComponentMap.EMPTY)), duplicates));
        for (var conflict : List.of(
                entry(3, original.modifier().operation(), original.slot(), original.display()),
                entry(2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, original.slot(), original.display()),
                entry(2, original.modifier().operation(), EquipmentSlotGroup.ANY, original.display()),
                entry(2, original.modifier().operation(), original.slot(), ItemAttributeModifiers.Display.hidden()))) {
            var normalized = SilentGearCompatibility.normalize(new ItemAttributeModifiers(List.of(original, original, conflict)));
            assertEquals(List.of(original, conflict), normalized.modifiers());
            var result = AttributeMerger.merge(normalized, ItemAttributeModifiers.EMPTY, id -> AttributePolicy.AVERAGE);
            assertEquals(normalized, result.primary());
            assertEquals(ItemAttributeModifiers.EMPTY, result.inverse());
        }
    }

    @Test
    void stateCodecRejectsInvalidAuthorityAndBoundsWithoutNativeClasses() {
        var state = new SilentGearState(Optional.of(new SilentGearState.Oxidation("oxidized", 2, 1, 17)), Optional.empty(),
                AttributePolicy.AVERAGE, List.of(new SilentGearState.Projection(
                new AttributeModifier(SilentGearState.MODIFIER, 3, AttributeModifier.Operation.ADD_VALUE),
                ItemAttributeModifiers.Display.hidden())), true, Optional.of(new SilentGearState.Sample(5, 100, "definition")));
        JsonObject valid = SilentGearState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        assertEquals(state, SilentGearState.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow());
        for (String key : List.of("phase", "level", "stage", "counter")) {
            for (int bound : List.of(-1, Integer.MAX_VALUE)) {
                var malformed = valid.deepCopy();
                if (key.equals("phase")) { malformed.getAsJsonObject("left").addProperty(key, "unknown"); }
                else { malformed.getAsJsonObject("left").addProperty(key, bound); }
                assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, malformed).isError(), key);
            }
        }
        var badPolicy = valid.deepCopy();
        badPolicy.addProperty("armor_policy", "unknown");
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, badPolicy).isError());
        var oversized = valid.deepCopy();
        var entries = new JsonArray();
        for (int i = 0; i < 17; i++) { entries.add(valid.getAsJsonArray("armor_projection").get(0).deepCopy()); }
        oversized.add("armor_projection", entries);
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, oversized).isError());
        var wrongIdentity = valid.deepCopy();
        wrongIdentity.getAsJsonArray("armor_projection").get(0).getAsJsonObject().getAsJsonObject("modifier").addProperty("id", "test:foreign");
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, wrongIdentity).isError());
        var badSample = valid.deepCopy();
        badSample.getAsJsonObject("sample").addProperty("damage", 101);
        assertTrue(SilentGearState.CODEC.parse(JsonOps.INSTANCE, badSample).isError());
        assertThrows(IllegalArgumentException.class, () -> new SilentGearState.Sample(101, 100, ""));
        assertThrows(IllegalArgumentException.class, () -> new SilentGearState.Projection(
                new AttributeModifier(SilentGearState.MODIFIER, Double.NaN, AttributeModifier.Operation.ADD_VALUE),
                ItemAttributeModifiers.Display.hidden()));
    }

    @Test
    void onlyMagicArmorDefaultIsAdded() {
        var defaults = AttributeRuleDefaults.ruleSet();
        assertEquals(AttributePolicy.AVERAGE, defaults.policyFor(Identifier.parse("silentgear:magic_armor")));
        assertEquals(AttributePolicy.AVERAGE, defaults.policyFor(Identifier.withDefaultNamespace("armor")));
        assertEquals(AttributePolicy.MAXIMUM, defaults.policyFor(Identifier.withDefaultNamespace("step_height")));
        assertEquals(AttributePolicy.SEPARATE, defaults.policyFor(Identifier.parse("test:unknown")));
    }

    @Test
    void existingExplicitMagicPolicySurvivesNormalConfigLoading(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("attributes.toml");
        java.nio.file.Files.writeString(file, "[attributes]\nfallback = 'separate'\n[attributes.\"silentgear:magic_armor\"]\npolicy = 'maximum'\n");
        var config = new AttributeRulesConfig(file);
        assertEquals(AttributePolicy.MAXIMUM, config.load().policyFor(Identifier.parse("silentgear:magic_armor")));
        assertEquals(AttributePolicy.MAXIMUM, config.load().policyFor(Identifier.parse("silentgear:magic_armor")));
    }

    private static ItemAttributeModifiers.Entry entry(double amount, AttributeModifier.Operation operation,
                                                      EquipmentSlotGroup slot, ItemAttributeModifiers.Display display) {
        return new ItemAttributeModifiers.Entry(Attributes.LUCK,
                new AttributeModifier(Identifier.parse("test:trait"), amount, operation), slot, display);
    }
}
