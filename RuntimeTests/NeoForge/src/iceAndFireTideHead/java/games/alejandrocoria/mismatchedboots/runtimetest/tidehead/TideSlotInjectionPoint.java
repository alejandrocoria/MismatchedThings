package games.alejandrocoria.mismatchedboots.runtimetest.tidehead;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Unique pinned slot operation only; complete consumer safety belongs to the final verifier. */
public final class TideSlotInjectionPoint extends InjectionPoint {
    private final String slot;

    public TideSlotInjectionPoint(InjectionPointData data) {
        super(data);
        slot = data.get("slot", "FEET");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        AbstractInsnNode candidate = null;
        for (var instruction : instructions) {
            if (!(instruction instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.INSTANCEOF
                    || !type.desc.equals("com/iafenvoy/iceandfire/item/armor/SeaSerpentArmorItem")) {
                continue;
            }
            AbstractInsnNode read = instruction;
            for (int count = 0; count < 3 && read != null; count++) {
                do { read = read.getPrevious(); } while (read != null && read.getOpcode() < 0);
            }
            if (read instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals("net/minecraft/world/entity/EquipmentSlot") && field.name.equals(slot)) {
                if (candidate != null) { return false; } // Ambiguity must skip our hook, not inject twice or abort startup.
                candidate = instruction;
            }
        }
        if (candidate == null) { return false; }
        nodes.add(candidate);
        return true;
    }
}
