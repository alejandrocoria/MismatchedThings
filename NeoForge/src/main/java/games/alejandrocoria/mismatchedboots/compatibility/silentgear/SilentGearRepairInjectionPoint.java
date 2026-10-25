package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

public final class SilentGearRepairInjectionPoint extends InjectionPoint {
    public SilentGearRepairInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var call = SilentGearEffectShape.repairCall(descriptor, instructions);
        if (call == null) {
            return false;
        }
        nodes.add(call);
        return true;
    }
}
