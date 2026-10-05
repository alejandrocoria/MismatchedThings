package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local selector for the guarded handler: no global class/slot identity projection. */
public final class IceAndFireDamageFeetInjectionPoint extends InjectionPoint {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public IceAndFireDamageFeetInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        // Other mixins can change the method after the plugin inspected it. Disable rather than fail startup.
        if (!matchesAuditedInstructions(descriptor, instructions)) {
            if (WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Ice and Fire projectile/dragon handler was transformed; boot compatibility disabled");
            }
            return false;
        }
        var checks = IceAndFireDamageShape.feetChecks(instructions);
        nodes.addAll(checks);
        return !checks.isEmpty();
    }

    static boolean matchesAuditedInstructions(String descriptor, InsnList instructions) {
        if (!IceAndFireDamageShape.DESCRIPTOR.equals(descriptor)) {
            return false;
        }
        MethodNode view = new MethodNode();
        view.name = IceAndFireDamageShape.METHOD;
        view.desc = descriptor;
        view.instructions = instructions;
        return IceAndFireDamageShape.AUDITED_FINGERPRINT.equals(IceAndFireDamageShape.fingerprint(view))
                && IceAndFireDamageShape.feetChecks(instructions).size() == 3;
    }
}
