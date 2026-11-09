package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

public final class SetPoint extends InjectionPoint {
    private final boolean type;

    public SetPoint(InjectionPointData data) {
        super(data);
        type = data.get("predicate", "type").equals("type");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var pair = SetShape.pair(descriptor, instructions, false);
        if (pair == null) {
            return false;
        }
        nodes.add(type ? pair.type() : pair.trait());
        return true;
    }
}
