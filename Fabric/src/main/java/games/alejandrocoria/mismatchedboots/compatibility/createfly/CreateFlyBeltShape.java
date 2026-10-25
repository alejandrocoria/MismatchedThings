package games.alejandrocoria.mismatchedboots.compatibility.createfly;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** The crouching-player Cardboard exit, not every ItemStack.is call in entityInside. */
public final class CreateFlyBeltShape {
    static final String DESCRIPTOR = "(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    private static final String STACK = "net/minecraft/world/item/ItemStack";

    private CreateFlyBeltShape() {
    }

    public static List<AbstractInsnNode> cardboardChecks(InsnList instructions) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (var node : instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != Opcodes.NOP) {
                code.add(node);
            }
        }
        List<AbstractInsnNode> matches = new ArrayList<>();
        for (int index = 7; index + 2 < code.size(); index++) {
            if (call(code.get(index), STACK, "is", "(Ljava/lang/Object;)Z")
                    && code.get(index - 7) instanceof VarInsnNode shiftPlayer && shiftPlayer.getOpcode() == Opcodes.ALOAD
                    && call(code.get(index - 6), PLAYER, "isShiftKeyDown", "()Z")
                    && code.get(index - 5) instanceof JumpInsnNode shift && shift.getOpcode() == Opcodes.IFEQ
                    && code.get(index - 4) instanceof VarInsnNode feetPlayer && feetPlayer.getOpcode() == Opcodes.ALOAD
                    && feetPlayer.var == shiftPlayer.var
                    && field(code.get(index - 3), SLOT, "FEET", "L" + SLOT + ";")
                    && call(code.get(index - 2), PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")
                    && field(code.get(index - 1), "com/zurrtum/create/AllItems", "CARDBOARD_BOOTS",
                             "Lcom/zurrtum/create/content/equipment/armor/CardboardArmorItem;")
                    && code.get(index + 1) instanceof JumpInsnNode item && item.getOpcode() == Opcodes.IFNE
                    && item.label == shift.label && code.get(index + 2).getOpcode() == Opcodes.RETURN
                    && nextCode(item.label) == nextCode(code.get(index + 2))) {
                matches.add(code.get(index));
            }
        }
        return matches.size() == 1 ? List.copyOf(matches) : List.of();
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }

    private static boolean field(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && owner.equals(field.owner) && name.equals(field.name) && descriptor.equals(field.desc);
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        var next = node.getNext();
        while (next != null && (next.getOpcode() < 0 || next.getOpcode() == Opcodes.NOP)) {
            next = next.getNext();
        }
        return next;
    }
}
