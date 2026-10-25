package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

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
        var input = new ArrayList<>(List.of("earthquake", "stepheight", "jumpboost"));
        var credits = new JustDireRefundCredits(input);
        input.clear();
        assertEquals(List.of("stepheight", "jumpboost", "earthquake"), credits.names());
        assertEquals("[\"stepheight\",\"jumpboost\",\"earthquake\"]",
                     JustDireRefundCredits.CODEC.encodeStart(JsonOps.INSTANCE, credits).getOrThrow().toString());
        assertThrows(UnsupportedOperationException.class, () -> credits.names().add("groundstomp"));
    }

    @Test
    void programmaticCreditsCannotRepresentDuplicateOrUnsupportedPurchases() {
        assertThrows(IllegalArgumentException.class, () -> new JustDireRefundCredits(List.of("jumpboost", "jumpboost")));
        assertThrows(IllegalArgumentException.class, () -> new JustDireRefundCredits(List.of("lavarepair")));
    }

    @Test
    void persistentCodecRejectsInvalidEntitlementsAndCanonicalizesValidOnes() {
        for (String invalid : List.of("[\"jumpboost\",\"jumpboost\"]", "[\"lavarepair\"]",
                                      "[\"stepheight\",\"jumpboost\",\"groundstomp\",\"negatefalldamage\",\"earthquake\",\"extra\"]")) {
            assertTrue(JustDireRefundCredits.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), invalid);
        }
        assertEquals(List.of("stepheight", "earthquake"), JustDireRefundCredits.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("[\"earthquake\",\"stepheight\"]")).getOrThrow().names());
        assertTrue(JustDireRefundCredits.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).getOrThrow().names().isEmpty());
    }
}
