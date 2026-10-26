package games.alejandrocoria.mismatchedboots.compatibility.createfly;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CreateFlyBeltInjectionPoint extends InjectionPoint {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public CreateFlyBeltInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var checks = CreateFlyBeltShape.DESCRIPTOR.equals(descriptor)
                     ? CreateFlyBeltShape.cardboardChecks(instructions) : java.util.List.<AbstractInsnNode>of();
        if (checks.isEmpty()) {
            if (WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Create Fly Belt local contract is incompatible; Cardboard Belt compatibility disabled");
            }
            return false;
        }
        nodes.addAll(checks);
        return true;
    }
}
