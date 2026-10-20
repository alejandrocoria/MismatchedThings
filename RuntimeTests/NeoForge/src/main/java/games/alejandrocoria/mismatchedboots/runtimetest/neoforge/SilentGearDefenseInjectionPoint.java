package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Pinned test-only site, not a production admission proof. */
public final class SilentGearDefenseInjectionPoint extends InjectionPoint {
    public SilentGearDefenseInjectionPoint(InjectionPointData data) { super(data); }
    @Override public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        for (var instruction : instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && type.desc.equals("net/silentchaos512/gear/api/item/GearArmor")) { nodes.add(type); }
        }
        return nodes.size() == 1;
    }
}
