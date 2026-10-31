package games.alejandrocoria.mismatchedleggings.config;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/** Product-owned lifecycle; no Boots state, shared config file or client filesystem reads. */
public final class CompositionRulesLifecycle {
    private CompositionRulesLifecycle() {
    }

    public static void registerContributor() {
        MismatchedWorkbench.registerContributor(
                Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "leggings"), LeggingHalf::isValidSource);
    }

    public static void load(Path directory) {
        var snapshot = CompositionRules.load(directory);
        CompositionRuleState.installServer(snapshot);
        LeggingsContent.LOG.info("Loaded leggings composition rules: {} attributes, {} component declarations",
                snapshot.attributes().rules().size(), snapshot.components().declarations().size());
    }

    public static void stopped() {
        CompositionRuleState.installServer(CompositionRules.defaults());
    }
}
