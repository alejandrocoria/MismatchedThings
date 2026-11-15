package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustDireRefundCreditsTest {
    @Test
    void entitlementHasStableOrderAndCannotAliasThePurchaseInput() {
        var input = new ArrayList<>(List.of("phase", "walkspeed", "runspeed"));
        var credits = new JustDireRefundCredits(input);
        input.clear();
        assertEquals(List.of("walkspeed", "runspeed", "phase"), credits.names());
        assertEquals("[\"walkspeed\",\"runspeed\",\"phase\"]",
                     JustDireRefundCredits.CODEC.encodeStart(JsonOps.INSTANCE, credits).getOrThrow().toString());
        assertThrows(UnsupportedOperationException.class, () -> credits.names().add("swimspeed"));
    }

    @Test
    void programmaticCreditsCannotRepresentDuplicateOrUnsupportedPurchases() {
        assertThrows(IllegalArgumentException.class, () -> new JustDireRefundCredits(List.of("runspeed", "runspeed")));
        assertThrows(IllegalArgumentException.class, () -> new JustDireRefundCredits(List.of("lavarepair")));
    }

    @Test
    void persistentCodecRejectsInvalidEntitlementsAndCanonicalizesValidOnes() {
        for (String invalid : List.of("[\"runspeed\",\"runspeed\"]", "[\"lavarepair\"]",
                                      "[\"walkspeed\",\"runspeed\",\"swimspeed\",\"decoy\",\"phase\",\"extra\"]")) {
            assertTrue(JustDireRefundCredits.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), invalid);
        }
        assertEquals(List.of("walkspeed", "phase"), JustDireRefundCredits.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("[\"phase\",\"walkspeed\"]")).getOrThrow().names());
        assertTrue(JustDireRefundCredits.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).getOrThrow().names().isEmpty());
    }
}
