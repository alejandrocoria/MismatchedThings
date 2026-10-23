package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MysticalAugmentsTest {
    @Test void bothOrientationsSelectStrongestOnceInStableFamilyOrder() {
        var left = List.of(spec("jump_boost_i"), spec("health_boost_v"));
        var right = List.of(spec("jump_boost_iii"), spec("health_boost_i"));
        var expected = List.of(spec("health_boost_v"), spec("jump_boost_iii"));
        assertEquals(expected, MysticalAugments.select(left, right));
        assertEquals(expected, MysticalAugments.select(right, left));
        assertEquals(expected, MysticalAugments.select(expected, expected));
    }

    @Test void fourNativeSlotsMaySupplyFourFamiliesWithoutInventingMixedCapacity() {
        assertEquals(List.of(spec("health_boost_i"), spec("jump_boost_iii"), spec("no_fall_damage"), spec("step_assist")),
                MysticalAugments.select(List.of(spec("jump_boost_iii"), spec("step_assist")),
                        List.of(spec("health_boost_i"), spec("no_fall_damage"))));
    }

    @Test void catalogAdmitsOnlyTheAuditedBootAugments() {
        assertEquals(23, MysticalAugments.CATALOG.size());
        for (String otherSlot : List.of("flight", "speed_i", "haste_i", "night_vision", "water_breathing")) {
            assertFalse(MysticalAugments.CATALOG.containsKey(Identifier.parse("mysticalagriculture:" + otherSlot)));
        }
    }

    private static MysticalAugments.Spec spec(String name) {
        return MysticalAugments.CATALOG.get(Identifier.parse("mysticalagriculture:" + name));
    }
}
