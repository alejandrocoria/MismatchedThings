package games.alejandrocoria.mismatchedthings.api.compatibility.aether;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AetherArmorSetFormatTest {
    private static final String HISTORY = "fixture:history";
    private static final Codec<String> RECORD = Codec.STRING.fieldOf("armor").codec();

    @Test
    void upgradesOnlyTheRootAndDirectSourceFieldsWithoutNormalizingOtherValues() {
        var original = JsonParser.parseString("""
                {"aether_ii:armor_set":"aether_ii:armor/neptune","fixture:enabled":true,
                 "minecraft:custom_data":{"aether_ii:armor_set":"opaque"},
                 "fixture:history":{
                   "left":{"id":"fixture:left","components":{"aether_ii:armor_set":"aether_ii:armor/zanite","fixture:enabled":false}},
                   "right":{"id":"fixture:right","components":{"!aether_ii:armor_set":{}}}}}
                """);
        var snapshot = original.deepCopy();
        var expected = original.deepCopy();
        expected.getAsJsonObject().add("aether_ii:armor_set", wrapped("aether_ii:armor/neptune"));
        expected.getAsJsonObject().getAsJsonObject(HISTORY).getAsJsonObject("left").getAsJsonObject("components")
                .add("aether_ii:armor_set", wrapped("aether_ii:armor/zanite"));

        var updated = AetherArmorSetFormat.upgradePatch(JsonOps.INSTANCE, original, HISTORY, RECORD);
        assertEquals(expected, updated);
        assertEquals(snapshot, original);
        assertSame(updated, AetherArmorSetFormat.upgradePatch(JsonOps.INSTANCE, updated, HISTORY, RECORD));
    }

    @Test
    void aThrowingForeignCodecDoesNotReplaceTheOriginalPatch() {
        var patch = JsonParser.parseString("{\"aether_ii:armor_set\":\"aether_ii:armor/neptune\"}");
        var throwing = RECORD.xmap(value -> {
            throw new IllegalArgumentException("Unavailable native data");
        }, value -> "unused");
        assertSame(patch, AetherArmorSetFormat.upgradePatch(JsonOps.INSTANCE, patch, HISTORY, throwing));
    }

    @Test
    void leavesTheOldCodecAndUnrecognizedValuesInCharge() {
        for (var raw : new String[]{"\"aether_ii:armor/neptune\"", "42", "{}", "{\"armor\":42}"}) {
            var patch = JsonParser.parseString("{\"aether_ii:armor_set\":" + raw + "}");
            assertSame(patch, AetherArmorSetFormat.upgradePatch(JsonOps.INSTANCE, patch, HISTORY, Codec.STRING));
            var rejecting = RECORD.validate(value -> DataResult.error(() -> "Unknown native tag"));
            assertSame(patch, AetherArmorSetFormat.upgradePatch(JsonOps.INSTANCE, patch, HISTORY, rejecting));
        }
    }

    private static JsonElement wrapped(String tag) {
        var result = new com.google.gson.JsonObject();
        result.addProperty("armor", tag);
        return result;
    }
}
