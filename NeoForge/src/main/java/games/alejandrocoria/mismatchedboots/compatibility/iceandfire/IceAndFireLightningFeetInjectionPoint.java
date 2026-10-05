package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
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
        if (!matchesLocalContract(descriptor, instructions)) {
            if (WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Ice and Fire lightning-set local contract is incompatible; its boot compatibility disabled");
            }
            return false;
        }
        var checks = IceAndFireDamageShape.lightningFeetChecks(instructions);
        nodes.addAll(checks);
        return !checks.isEmpty();
    }

    static boolean matchesLocalContract(String descriptor, InsnList instructions) {
        return IceAndFireDamageShape.DESCRIPTOR.equals(descriptor)
                && IceAndFireDamageShape.lightningFeetChecks(instructions).size() == 1;
    }
}
