package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;

import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility.Provider.BLAZEGOLD;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility.Provider.CELESTIGEM;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility.Provider.ECLIPSE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustDireGroundStompQuoteTest {
    @Test
    void strengthWinsBeforeTierAndCostIsNotAnotherRankingCriterion() {
        var eclipse = new JustDireGroundStompCompatibility.Quote(ECLIPSE, 1, 5_000, 200);
        var celestigem = new JustDireGroundStompCompatibility.Quote(CELESTIGEM, 3, 5_000, 200);
        var blazegold = new JustDireGroundStompCompatibility.Quote(BLAZEGOLD, 2, 25, 200);
        assertTrue(blazegold.preferredTo(eclipse));
        assertTrue(celestigem.preferredTo(blazegold));
        assertFalse(eclipse.preferredTo(celestigem));
        assertFalse(blazegold.preferredTo(celestigem));
    }

    @Test
    void equalEffectiveValueHasStableTierPriorityWithoutDuplicateSelection() {
        var eclipse = new JustDireGroundStompCompatibility.Quote(ECLIPSE, 2, 5_000, 200);
        var celestigem = new JustDireGroundStompCompatibility.Quote(CELESTIGEM, 2, 5_000, 200);
        var blazegold = new JustDireGroundStompCompatibility.Quote(BLAZEGOLD, 2, 25, 200);
        assertTrue(eclipse.preferredTo(celestigem));
        assertTrue(celestigem.preferredTo(blazegold));
        assertTrue(eclipse.preferredTo(blazegold));
        assertFalse(blazegold.preferredTo(eclipse));
        assertFalse(celestigem.preferredTo(eclipse));
        assertFalse(eclipse.preferredTo(eclipse));
    }
}
