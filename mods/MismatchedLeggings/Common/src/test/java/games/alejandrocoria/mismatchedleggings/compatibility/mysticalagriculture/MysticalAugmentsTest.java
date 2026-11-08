package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MysticalAugmentsTest {
    @Test void strongestFamiliesAreStableInBothOrientationsAndEqualTies() {
        var first = List.of(spec("speed_i"), spec("health_boost_v"));
        var second = List.of(spec("speed_iii"), spec("health_boost_i"));
        var expected = List.of(spec("health_boost_v"), spec("speed_iii"));
        assertEquals(expected, MysticalAugments.select(first, second));
        assertEquals(expected, MysticalAugments.select(second, first));
        assertEquals(expected, MysticalAugments.select(expected, expected));
    }
    @Test void sourceCapacityDoesNotCapCombinedFamilies() {
        assertEquals(List.of(spec("health_boost_i"), spec("luck_iii"), spec("speed_iii"), spec("step_assist")),
                MysticalAugments.select(List.of(spec("speed_iii"), spec("step_assist")), List.of(spec("health_boost_i"), spec("luck_iii"))));
    }
    @Test void catalogIsExactlyTheLeggingsInventoryWithNativeTierRequirements() {
        assertEquals(22, MysticalAugments.CATALOG.size());
        assertEquals(10, MysticalAugments.Family.values().length);
        for (String excluded : List.of("flight", "jump_boost_i", "no_fall_damage", "slow_falling", "night_vision")) {
            assertFalse(MysticalAugments.CATALOG.containsKey(Identifier.parse("mysticalagriculture:" + excluded)));
        }
        assertEquals(2, spec("speed_i").requiredTier());
        assertEquals(4, spec("speed_iii").requiredTier());
        assertEquals(3, spec("luck_ii").requiredTier());
        assertEquals(5, spec("health_boost_v").requiredTier());
        assertEquals(3, spec("slowness_resistance").requiredTier());
    }
    private static MysticalAugments.Spec spec(String id) {
        return MysticalAugments.CATALOG.get(Identifier.parse("mysticalagriculture:" + id));
    }
}
