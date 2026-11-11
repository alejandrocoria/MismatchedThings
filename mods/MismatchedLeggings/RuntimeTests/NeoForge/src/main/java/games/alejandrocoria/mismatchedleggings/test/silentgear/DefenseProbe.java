package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.util.GearData;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Counts only selected native leaf attempts and detects forbidden mixed-root recalculation. */
public final class DefenseProbe {
    static int calls;
    static boolean failSecond;
    private static ItemStack calibration;

    private DefenseProbe() {}

    public static void before() {
        calls++;
        if (failSecond && calls == 2) {
            failSecond = false;
            throw new IllegalStateException("Deliberate second selected-call failure");
        }
    }

    public static void recalculation(ItemStack stack) {
        if (stack == calibration || MixedLeggingsStructure.isMixedItem(stack)) {
            throw new AssertionError("Native gear recalculation received a protected defense context");
        }
    }

    static void calibrate(ServerPlayer player) throws Exception {
        calibration = FoundationChecks.armor("iron");
        boolean caught = false;
        try {
            GearData.recalculateGearData(calibration, player);
        } catch (AssertionError expected) {
            caught = true;
        } finally {
            calibration = null;
        }
        require(caught, "Native defense writer tripwire was not calibrated");
    }
}
