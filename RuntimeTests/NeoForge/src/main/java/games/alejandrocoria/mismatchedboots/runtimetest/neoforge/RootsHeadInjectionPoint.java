package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

public final class RootsHeadInjectionPoint extends InjectionPoint {
    public RootsHeadInjectionPoint(InjectionPointData data) {
        super(data);
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        boolean head = false;
        for (var instruction : instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("HEAD")) {
                head = true;
            }
            if (head && instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("elucent/rootsclassic/item/SylvanArmorItem")) {
                nodes.add(type);
                return true;
            }
        }
        return false;
    }
}
