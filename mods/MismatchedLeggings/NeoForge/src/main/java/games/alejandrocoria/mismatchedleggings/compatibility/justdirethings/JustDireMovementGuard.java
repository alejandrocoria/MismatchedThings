package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class JustDireMovementGuard {
    private static MethodHandle getter;
    private static boolean inspected;

    private JustDireMovementGuard() {}

    static synchronized boolean ready() {
        if (!inspected) {
            inspected = true;
            try {
                var owner = Class.forName(JustDireMovementShape.OWNER.replace('/', '.'));
                getter = MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findStatic(owner,
                        JustDireMovementShape.GETTER, MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
        }
        try { return getter != null && (boolean) getter.invokeExact(); }
        catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            getter = null;
            return false;
        }
    }
}
