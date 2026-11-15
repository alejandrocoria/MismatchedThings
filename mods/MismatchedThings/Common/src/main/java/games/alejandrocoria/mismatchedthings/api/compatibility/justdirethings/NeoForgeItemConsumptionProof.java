package games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings;

import games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings.JustDireRefundShape;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Pure bytecode inspection for the NeoForge exhausted-stack boundary, since Things 1.1.0.
 * Safe during mixin discovery: no game/loader classes or native bindings are initialized.
 * MA consumers retain their own observation, marker and receipt proofs.
 */
public final class NeoForgeItemConsumptionProof {
    private NeoForgeItemConsumptionProof() {
    }

    public static boolean boundary(ClassNode stack) {
        return JustDireRefundShape.boundary(stack);
    }

    public static boolean observer(MethodNode handler) {
        return JustDireRefundShape.observer(handler);
    }

    public static boolean siteHandler(ClassNode stack, MethodNode handler) {
        return JustDireRefundShape.siteHandler(stack, handler);
    }
}
