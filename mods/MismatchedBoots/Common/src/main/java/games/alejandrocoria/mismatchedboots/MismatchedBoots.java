package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesNetwork;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.resources.Identifier;

public class MismatchedBoots {
    public static void init() {
        MismatchedWorkbench.registerContributor(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "boots"), BootHalf::isValidSource);
        CompositionRulesNetwork.register();
    }
}
