package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustDireActiveClockTest {
    @Test
    void phaseTransitionAndExpirySpendTheSameBoundedClockBudget() {
        var active = new JustDireActiveState.Clock(1, true);
        assertTrue(active.progressesTo(new JustDireActiveState.Clock(800, false), 800, 1));
        assertFalse(active.progressesTo(new JustDireActiveState.Clock(799, false), 800, 1));
        assertFalse(active.progressesTo(null, 800, 40));
        assertTrue(new JustDireActiveState.Clock(1, false).progressesTo(null, 800, 1));
        assertTrue(new JustDireActiveState.Clock(0, false).progressesTo(null, 0, 1));
    }

    @Test
    void resetsWrongPhasesUnsupportedCountersAndTooMuchProgressDoNotMatch() {
        var cooldown = new JustDireActiveState.Clock(10, false);
        assertFalse(cooldown.progressesTo(new JustDireActiveState.Clock(11, false), 800, 40));
        assertFalse(cooldown.progressesTo(new JustDireActiveState.Clock(1, true), 800, 40));
        assertFalse(cooldown.progressesTo(null, 800, 9));
        assertFalse(new JustDireActiveState.Clock(-1, false).progressesTo(null, 800, 40));
        assertFalse(new JustDireActiveState.Clock(0, true).progressesTo(null, 800, 40));
        assertFalse(new JustDireActiveState.Clock(3, true).progressesTo(new JustDireActiveState.Clock(2, true), 0, 1));
        assertFalse(new JustDireActiveState.Clock(Integer.MAX_VALUE, true).progressesTo(null, 800, 40));
    }
}
