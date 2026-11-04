package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.resources.Identifier;

/** Wire revision, independent of Boots releases and other family products. */
public final class CompositionRulesProtocol {
    public static final String REVISION = "2";
    public static final Identifier READY_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ready_v" + REVISION);
    public static final Identifier RULES_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "rules_v" + REVISION);

    private CompositionRulesProtocol() {
    }
}
