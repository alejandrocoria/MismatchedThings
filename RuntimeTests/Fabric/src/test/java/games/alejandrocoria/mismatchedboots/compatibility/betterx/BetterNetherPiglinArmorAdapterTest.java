package games.alejandrocoria.mismatchedboots.compatibility.betterx;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BetterNetherPiglinArmorAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void successfulBoundaryReadsLiveConfigurationAndAllArmorSets() throws ReflectiveOperationException {
        Toggle toggle = new Toggle();
        ArmorSet first = new ArmorSet();
        ArmorSet second = new ArmorSet(Items.IRON_BOOTS);
        ArmorSet third = new ArmorSet(Items.DIAMOND_BOOTS);
        List<String> warnings = new ArrayList<>();
        var boundary = boundary(toggle, new Object[]{first, second, third},
                ArmorSet.class.getMethod("getArmorPieces"), warnings);

        assertTrue(boundary.isEnabled());
        assertTrue(boundary.contains(Items.IRON_BOOTS));
        assertTrue(boundary.contains(Items.DIAMOND_BOOTS));
        assertFalse(boundary.contains(Items.GOLDEN_BOOTS));
        toggle.enabled = false;
        assertFalse(boundary.isEnabled());
        toggle.enabled = true;
        assertTrue(boundary.isEnabled());
        assertEquals(3, toggle.queries);
        assertEquals(List.of(), warnings);
    }

    @Test
    void brokenConfigurationDisablesBothOperationsAndWarnsOnlyOnce() throws ReflectiveOperationException {
        Toggle toggle = new Toggle();
        toggle.broken = true;
        ArmorSet set = new ArmorSet(Items.IRON_BOOTS);
        List<String> warnings = new ArrayList<>();
        var boundary = boundary(toggle, new Object[]{set}, ArmorSet.class.getMethod("getArmorPieces"), warnings);

        assertFalse(boundary.isEnabled());
        toggle.broken = false;
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(boundary.isEnabled());
            assertFalse(boundary.contains(Items.IRON_BOOTS));
        }
        assertEquals(1, toggle.queries);
        assertEquals(0, set.queries);
        assertEquals(List.of("Could not read Better Nether piglin configuration; compatibility disabled"), warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"throws", "wrongType", "null"})
    void brokenArmorQueryDisablesBothOperationsAndWarnsOnlyOnce(String failure) throws ReflectiveOperationException {
        Toggle toggle = new Toggle();
        BrokenArmorSet set = new BrokenArmorSet(failure);
        List<String> warnings = new ArrayList<>();
        var boundary = boundary(toggle, new Object[]{set}, BrokenArmorSet.class.getMethod("getArmorPieces"), warnings);

        assertTrue(boundary.isEnabled());
        assertFalse(boundary.contains(Items.IRON_BOOTS));
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(boundary.contains(Items.IRON_BOOTS));
            assertFalse(boundary.isEnabled());
        }
        assertEquals(1, toggle.queries);
        assertEquals(1, set.queries);
        assertEquals(List.of("Could not query Better Nether armor sets; compatibility disabled"), warnings);
    }

    @Test
    void inaccessibleArmorMethodAlsoDisablesTheBoundary() throws ReflectiveOperationException {
        Toggle toggle = new Toggle();
        List<String> warnings = new ArrayList<>();
        var boundary = boundary(toggle, new Object[]{new InaccessibleArmorSet()},
                InaccessibleArmorSet.class.getDeclaredMethod("getArmorPieces"), warnings);

        assertFalse(boundary.contains(Items.IRON_BOOTS));
        assertFalse(boundary.contains(Items.IRON_BOOTS));
        assertFalse(boundary.isEnabled());
        assertEquals(1, warnings.size());
        assertEquals(0, toggle.queries);
    }

    private static BetterNetherPiglinArmorAdapter.Boundary boundary(
            Toggle toggle, Object[] sets, Method query, List<String> warnings) throws NoSuchMethodException {
        return new BetterNetherPiglinArmorAdapter.Boundary(toggle, Toggle.class.getMethod("get"),
                sets, query, (message, exception) -> warnings.add(message));
    }

    public static final class Toggle {
        boolean enabled = true;
        boolean broken;
        int queries;

        public Boolean get() {
            queries++;
            if (broken) {
                throw new IllegalStateException("Injected config failure");
            }
            return enabled;
        }
    }

    public static final class ArmorSet {
        private final Item[] pieces;
        int queries;

        ArmorSet(Item... pieces) {
            this.pieces = pieces;
        }

        public Item[] getArmorPieces() {
            queries++;
            return pieces;
        }
    }

    public static final class BrokenArmorSet {
        private final String failure;
        int queries;

        BrokenArmorSet(String failure) {
            this.failure = failure;
        }

        public Object getArmorPieces() {
            queries++;
            return switch (failure) {
                case "throws" -> throw new IllegalStateException("Injected armor query failure");
                case "wrongType" -> "Not an armor array";
                default -> null;
            };
        }
    }

    public static final class InaccessibleArmorSet {
        private Item[] getArmorPieces() {
            throw new AssertionError("Inaccessible method was invoked");
        }
    }
}
