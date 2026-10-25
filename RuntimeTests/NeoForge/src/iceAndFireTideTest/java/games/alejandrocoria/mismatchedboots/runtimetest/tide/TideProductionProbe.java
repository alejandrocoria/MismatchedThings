package games.alejandrocoria.mismatchedboots.runtimetest.tide;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideMixinPlugin;
import net.neoforged.fml.common.Mod;

/** Test-only counters and adversaries, not a second armor owner or activation bridge. */
@Mod("mismatchedboots_tide_test")
public final class TideProductionProbe {
    public static int activations, warnings, bindings;

    public TideProductionProbe() {
        if (TideFixturePlugin.MODE.isEmpty()) {
            throw new IllegalStateException("Tide fixture is test-only");
        }
    }

    public static int ownerActivations() {
        return activations;
    }

    public static int ownerWarnings() {
        return warnings;
    }

    public static int ownerBindingAttempts() {
        return bindings;
    }

    public static int ownerApiAvailable() {
        return new IceAndFireTideMixinPlugin().shouldApplyMixin("", "") ? 1 : 0;
    }

    public static void verifyMutation() {
        String mode = TideFixturePlugin.MODE;
        TidePredicateMutation.verify(mode);
        if (mode.equals("late") && TideCarrierProbe.reads == 0
                || mode.startsWith("base") && TideCarrierProbe.baseReads == 0
                || mode.equals("baseMutate") && TideCarrierProbe.baseMutations == 0
                || (mode.equals("extraWork") || mode.startsWith("chain")) && TideCarrierProbe.extraCalls == 0) {
            throw new AssertionError("Production adversarial fixture did not execute: " + mode);
        }
    }
}
