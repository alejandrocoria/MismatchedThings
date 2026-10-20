package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Select the paired local producers together, rather than an arbitrary GearArmor check. */
public final class SilentGearSetInjectionPoint extends InjectionPoint {
    private final boolean type;
    public SilentGearSetInjectionPoint(InjectionPointData data) { super(data); type = data.get("predicate", "type").equals("type"); }
    @Override public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var pair = SilentGearWearerShape.pair(descriptor, instructions);
        if (pair == null) { return false; }
        nodes.add(type ? pair.type() : pair.trait());
        return true;
    }
}
