package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Select neither site unless both predicates still share the audited producer and increment. */
public final class SilentGearSetInjectionPoint extends InjectionPoint {
    private final boolean type;

    public SilentGearSetInjectionPoint(InjectionPointData data) {
        super(data);
        type = data.get("predicate", "type").equals("type");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var pair = SilentGearSetShape.pair(descriptor, instructions, false, "mismatchedleggings$sgSetType", "mismatchedleggings$sgSetTrait");
        if (pair == null) { return false; }
        nodes.add(type ? pair.type() : pair.trait());
        return true;
    }
}
