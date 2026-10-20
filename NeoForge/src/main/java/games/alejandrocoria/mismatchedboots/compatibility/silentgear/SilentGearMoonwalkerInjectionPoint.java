package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Only the Moonwalker resource producer in the fall handler, never the neighboring Bounce query. */
public final class SilentGearMoonwalkerInjectionPoint extends InjectionPoint {
    public SilentGearMoonwalkerInjectionPoint(InjectionPointData data) { super(data); }
    @Override public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var call = SilentGearRecognitionShape.moonQuery(descriptor, instructions);
        if (call == null) { return false; }
        nodes.add(call); return true;
    }
}
