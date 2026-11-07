package games.alejandrocoria.mismatchedleggings.test.rootsclassic;

import java.util.ArrayList;
import java.util.List;

/** Scoped native branch forcing/counters only; never substitutes a repair or Terra implementation. */
public final class RootsTickTestControl {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private RootsTickTestControl() {
    }

    public static Scope begin(int... results) {
        if (ACTIVE.get() != null) {
            throw new AssertionError("Nested Roots test scope");
        }
        if (results.length == 0) {
            throw new AssertionError("Missing native roll results");
        }
        Scope scope = new Scope(results);
        ACTIVE.set(scope);
        return scope;
    }

    public static void tick() {
        Scope scope = ACTIVE.get();
        if (scope != null) {
            scope.ticks++;
        }
    }

    public static void repair() {
        Scope scope = ACTIVE.get();
        if (scope != null) {
            scope.repairs++;
        }
    }

    /** Harmless in the fixture, but not an audited native stack consumer the production guard may trust. */
    public static void foreignStack(Object stack) {
    }

    /** Deliberately unknown local predicate consumer, while leaving native controls functional. */
    public static net.minecraft.world.item.Item foreignItem(net.minecraft.world.item.Item item) {
        return item;
    }

    public static Integer roll(int bound) {
        Scope scope = ACTIVE.get();
        if (scope == null) {
            return null;
        }
        scope.bounds.add(bound);
        return scope.results[Math.min(scope.bounds.size() - 1, scope.results.length - 1)];
    }

    public static final class Scope implements AutoCloseable {
        final int[] results;
        final List<Integer> bounds = new ArrayList<>();
        int ticks;
        int repairs;

        private Scope(int[] results) {
            this.results = results;
        }

        @Override
        public void close() {
            ACTIVE.remove();
        }
    }
}
