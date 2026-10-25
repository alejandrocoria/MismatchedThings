package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

public final class JustDireJumpFeetInjectionPoint extends InjectionPoint {
    public JustDireJumpFeetInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var read = JustDireJumpShape.feetRead(descriptor, instructions);
        if (read == null) {
            return false;
        }
        nodes.add(read);
        return true;
    }
}
