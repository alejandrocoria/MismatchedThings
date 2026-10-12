package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustDireEarthquakePendingTest {
    @Test
    void subsequentCooldownCountsWhenSelectingTheLongestPendingLockout() {
        var active = new JustDireEarthquakeCompatibility.Pending(200, true);
        var inactive = new JustDireEarthquakeCompatibility.Pending(800, false);
        assertTrue(active.preferredTo(inactive, 800));
        assertFalse(inactive.preferredTo(active, 800));
        assertEquals(1_000, active.remaining(800));
        assertTrue(new JustDireEarthquakeCompatibility.Pending(1_001, false).preferredTo(active, 800));
    }

    @Test
    void equalTotalLockoutPrefersActivePhaseIndependentlyOfInputOrientation() {
        var active = new JustDireEarthquakeCompatibility.Pending(100, true);
        var inactive = new JustDireEarthquakeCompatibility.Pending(900, false);
        assertTrue(active.preferredTo(inactive, 800));
        assertFalse(inactive.preferredTo(active, 800));
        assertFalse(active.preferredTo(new JustDireEarthquakeCompatibility.Pending(100, true), 800));
    }

    @Test
    void largeNativeCountersCannotOverflowIntoShorterLockouts() {
        var active = new JustDireEarthquakeCompatibility.Pending(Integer.MAX_VALUE, true);
        var inactive = new JustDireEarthquakeCompatibility.Pending(Integer.MAX_VALUE, false);
        assertEquals((long) Integer.MAX_VALUE + 800, active.remaining(800));
        assertTrue(active.preferredTo(inactive, 800));
    }

    @Test
    void nonPositiveCountersCannotBecomeSupportedPendingClocks() {
        for (int ticks : new int[]{Integer.MIN_VALUE, -1, 0}) {
            assertFalse(new JustDireEarthquakeCompatibility.Pending(ticks, true).supported());
            assertFalse(new JustDireEarthquakeCompatibility.Pending(ticks, false).supported());
        }
        assertTrue(new JustDireEarthquakeCompatibility.Pending(1, true).supported());
        assertTrue(new JustDireEarthquakeCompatibility.Pending(Integer.MAX_VALUE, false).supported());
    }
}
