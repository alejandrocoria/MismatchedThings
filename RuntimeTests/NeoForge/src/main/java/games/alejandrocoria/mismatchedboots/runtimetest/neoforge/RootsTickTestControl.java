package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import java.util.ArrayList;
import java.util.List;

/** Scoped native branch forcing/counters only; never substitutes a repair or Terra implementation. */
public final class RootsTickTestControl {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private RootsTickTestControl() {
    }

    public static Scope begin(int result) {
        if (ACTIVE.get() != null) {
            throw new AssertionError("Nested Roots test scope");
        }
        Scope scope = new Scope(result);
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

    /** Value-preserving, but deliberately outside the audited local set predicate shape. */
    public static net.minecraft.world.item.Item foreignItem(net.minecraft.world.item.Item item) {
        return item;
    }

    public static Integer roll(int bound) {
        Scope scope = ACTIVE.get();
        if (scope == null) {
            return null;
        }
        scope.bounds.add(bound);
        return scope.result;
    }

    public static final class Scope implements AutoCloseable {
        final int result;
        final List<Integer> bounds = new ArrayList<>();
        int ticks, repairs;

        private Scope(int result) {
            this.result = result;
        }

        @Override
        public void close() {
            ACTIVE.remove();
        }
    }
}
