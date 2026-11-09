package games.alejandrocoria.mismatchedleggings.test.iafspike;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.ArrayList;
import java.util.Collection;

/** Native operand selector only. Production consumer/provenance proofs remain future work. */
@InjectionPoint.AtCode("LEGGINGS_IAF_SPIKE")
public final class LegsPoint extends InjectionPoint {
    private final String kind;

    public LegsPoint(InjectionPointData data) {
        super(data);
        kind = data.get("kind", "damage");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        var code = new ArrayList<AbstractInsnNode>();
        for (var instruction : instructions) {
            if (instruction.getOpcode() >= 0) { code.add(instruction); }
        }
        var found = new ArrayList<AbstractInsnNode>();
        for (int index = 4; index < code.size(); index++) {
            var instruction = code.get(index);
            if (kind.equals("lightning")) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack")
                        && call.name.equals("is") && code.get(index - 3) instanceof FieldInsnNode item
                        && item.owner.equals("com/iafenvoy/iceandfire/registry/IafItems")
                        && item.name.equals("DRAGONSTEEL_LIGHTNING_LEGGINGS")) {
                    found.add(instruction);
                }
                continue;
            }
            if (!(instruction instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.INSTANCEOF
                    || !(code.get(index - 3) instanceof FieldInsnNode slot) || !slot.name.equals("LEGS")
                    || !slot.owner.equals("net/minecraft/world/entity/EquipmentSlot")) {
                continue;
            }
            boolean tide = type.desc.endsWith("/SeaSerpentArmorItem");
            boolean damage = type.desc.endsWith("/TrollArmorItem") || type.desc.endsWith("/DragonScaleArmorItem")
                    || type.desc.endsWith("/DragonSteelArmorItem");
            if (kind.equals("tide") ? tide : damage) { found.add(instruction); }
        }
        if (found.size() != (kind.equals("damage") ? 3 : 1)) { return false; }
        nodes.addAll(found);
        return true;
    }
}
