package games.alejandrocoria.mismatchedleggings.config;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Distinct server authority and client preview state, even in an integrated process. */
public final class CompositionRuleState {
    private static volatile CompositionRules server = CompositionRules.defaults();
    private static volatile @Nullable CompositionRules synchronizedPreview;
    private static final List<Runnable> PREVIEW_LISTENERS = new ArrayList<>();

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
        notifyPreviewListeners();
    }

    public static @Nullable CompositionRules synchronizedPreview() {
        return synchronizedPreview;
    }

    public static void resetPreview() {
        synchronizedPreview = null;
        notifyPreviewListeners();
    }

    /** Client-thread updates only; server authority never notifies optional viewers. */
    public static Runnable addPreviewListener(Runnable listener) {
        PREVIEW_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
        if (synchronizedPreview != null) {
            runPreviewListener(listener);
        }
        return () -> PREVIEW_LISTENERS.remove(listener);
    }

    private static void notifyPreviewListeners() {
        List.copyOf(PREVIEW_LISTENERS).forEach(CompositionRuleState::runPreviewListener);
    }

    private static void runPreviewListener(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException exception) {
            LeggingsContent.LOG.error("Composition preview listener failed", exception);
        }
    }
}
