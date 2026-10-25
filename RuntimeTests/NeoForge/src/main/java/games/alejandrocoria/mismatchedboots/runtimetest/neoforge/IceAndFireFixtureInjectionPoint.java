package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Independent fixture selector: slot-specific native operations, not the production guard. */
public final class IceAndFireFixtureInjectionPoint extends InjectionPoint {
    private final String slot;

    public IceAndFireFixtureInjectionPoint(InjectionPointData data) {
        super(data);
        slot = data.get("slot", "FEET");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        for (var instruction : instructions) {
            int offset;
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                    && (type.desc.endsWith("/TrollArmorItem") || type.desc.endsWith("/DragonScaleArmorItem")
                    || type.desc.endsWith("/DragonSteelArmorItem"))) {
                offset = 3;
            } else if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("is")
                    && call.desc.equals("(Ljava/lang/Object;)Z")) {
                offset = 5;
            } else {
                continue;
            }
            AbstractInsnNode read = instruction;
            for (int count = 0; count < offset && read != null; count++) {
                do {
                    read = read.getPrevious();
                } while (read != null && read.getOpcode() < 0);
            }
            if (read instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals("net/minecraft/world/entity/EquipmentSlot") && field.name.equals(slot)) {
                nodes.add(instruction);
            }
        }
        return !nodes.isEmpty();
    }
}
