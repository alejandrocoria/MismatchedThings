package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** The event-player VOID_WARD query only; neighboring native traits remain untouched. */
public final class SilentGearVoidWardInjectionPoint extends InjectionPoint {
    public SilentGearVoidWardInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var query = SilentGearRecognitionShape.voidQuery(descriptor, instructions);
        if (query == null) {
            return false;
        }
        nodes.add(query);
        return true;
    }
}
