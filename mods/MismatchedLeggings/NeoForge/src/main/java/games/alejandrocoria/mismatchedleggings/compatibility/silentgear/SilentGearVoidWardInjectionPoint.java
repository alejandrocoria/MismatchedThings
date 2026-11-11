package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Only the VOID_WARD armor query using this event's player. */
public final class SilentGearVoidWardInjectionPoint extends InjectionPoint {
    public SilentGearVoidWardInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var query = SilentGearVoidWardShape.query(descriptor, instructions);
        if (query == null) {
            return false;
        }
        nodes.add(query);
        return true;
    }
}
