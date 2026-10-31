package games.alejandrocoria.mismatchedleggings.config;

import java.util.Objects;

/** Distinct server authority and client preview state, even in an integrated process. */
public final class CompositionRuleState {
    private static volatile CompositionRules server = CompositionRules.defaults();
    private static volatile CompositionRules preview = CompositionRules.defaults();

    private CompositionRuleState() {
    }

    public static CompositionRules server() {
        return server;
    }

    public static CompositionRules preview() {
        return preview;
    }

    public static void installServer(CompositionRules rules) {
        server = Objects.requireNonNull(rules, "rules");
    }

    public static void installPreview(CompositionRules rules) {
        preview = Objects.requireNonNull(rules, "rules");
    }

    public static void resetPreview() {
        preview = CompositionRules.defaults();
    }
}
