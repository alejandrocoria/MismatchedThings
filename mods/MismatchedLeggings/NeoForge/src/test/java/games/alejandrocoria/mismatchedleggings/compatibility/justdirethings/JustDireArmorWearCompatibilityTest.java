package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JustDireArmorWearCompatibilityTest {
    @Test
    void auditedWearPriceUsesOneIntegerRequestAndNativeLinearDiscount() {
        assertEquals(200, JustDireArmorWearCompatibility.bill(2, 0));
        assertEquals(180, JustDireArmorWearCompatibility.bill(2, 1));
        assertEquals(140, JustDireArmorWearCompatibility.bill(2, 3));
        assertEquals(0, JustDireArmorWearCompatibility.bill(2, 10));
        assertEquals(0, JustDireArmorWearCompatibility.bill(0, 0));
        assertEquals(-1, JustDireArmorWearCompatibility.bill(Integer.MAX_VALUE, 0));
        assertEquals(-1, JustDireArmorWearCompatibility.bill(2, -1));
    }
}
