package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local selector for the one exact Lightning boot check, with an independent handler guard. */
public final class IceAndFireLightningFeetInjectionPoint extends InjectionPoint {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public IceAndFireLightningFeetInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        if (!matchesAuditedInstructions(descriptor, instructions)) {
            if (WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Ice and Fire lightning-set handler was transformed; its boot compatibility disabled");
            }
            return false;
        }
        var checks = IceAndFireDamageShape.lightningFeetChecks(instructions);
        nodes.addAll(checks);
        return !checks.isEmpty();
    }

    static boolean matchesAuditedInstructions(String descriptor, InsnList instructions) {
        if (!IceAndFireDamageShape.DESCRIPTOR.equals(descriptor)) {
            return false;
        }
        MethodNode view = new MethodNode();
        view.name = IceAndFireDamageShape.LIGHTNING_METHOD;
        view.desc = descriptor;
        view.instructions = instructions;
        return IceAndFireDamageShape.LIGHTNING_FINGERPRINT.equals(IceAndFireDamageShape.fingerprint(view))
                && IceAndFireDamageShape.lightningFeetChecks(instructions).size() == 1;
    }
}
