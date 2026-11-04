package games.alejandrocoria.mismatchedboots.network;

import games.alejandrocoria.mismatchedboots.Constants;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client-thread preview state only; server configuration lifecycles are independent. */
public final class CompositionRulesClientLifecycle {
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static @Nullable CompositionRules synchronizedRules;

    private CompositionRulesClientLifecycle() {
    }

    public static @Nullable CompositionRules synchronizedRules() {
        return synchronizedRules;
    }

    public static boolean areRulesSynchronized() {
        return synchronizedRules != null;
    }

    public static void onConnected(Runnable readySender) {
        onDisconnected();
        readySender.run();
    }

    public static void onDisconnected() {
        synchronizedRules = null;
        notifyListeners();
    }

    public static void install(CompositionRules rules) {
        synchronizedRules = Objects.requireNonNull(rules, "rules");
        notifyListeners();
    }

    public static Runnable addRulesListener(Runnable listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
        if (synchronizedRules != null) {
            runListener(listener);
        }
        return () -> LISTENERS.remove(listener);
    }

    private static void notifyListeners() {
        List.copyOf(LISTENERS).forEach(CompositionRulesClientLifecycle::runListener);
    }

    private static void runListener(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException exception) {
            Constants.LOG.error("Composition-rules listener failed", exception);
        }
    }
}
