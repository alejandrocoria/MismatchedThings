package games.alejandrocoria.mismatchedleggings.config;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Distinct server authority and client preview state, even in an integrated process. */
public final class CompositionRuleState {
    private static volatile CompositionRules server = CompositionRules.defaults();
    private static volatile @Nullable CompositionRules synchronizedPreview;

    private CompositionRuleState() {
    }

    public static CompositionRules server() {
        return server;
    }

    public static CompositionRules preview() {
        var snapshot = synchronizedPreview;
        return snapshot == null ? CompositionRules.defaults() : snapshot;
    }

    public static void installServer(CompositionRules rules) {
        server = Objects.requireNonNull(rules, "rules");
    }

    public static void installPreview(CompositionRules rules) {
        synchronizedPreview = Objects.requireNonNull(rules, "rules");
    }

    public static @Nullable CompositionRules synchronizedPreview() {
        return synchronizedPreview;
    }

    public static void resetPreview() {
        synchronizedPreview = null;
    }
}
