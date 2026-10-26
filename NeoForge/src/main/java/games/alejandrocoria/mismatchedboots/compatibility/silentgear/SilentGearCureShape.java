package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** The small concrete cancellation loop only: selected context-player, configured holders, no gear writes. */
final class SilentGearCureShape implements Opcodes {
    static final String OWNER = SilentGearEffectShape.PREFIX + "gear/trait/effect/CancelEffectsTraitEffect";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";

    private SilentGearCureShape() {
    }

    static boolean admitted(ClassNode owner) {
        var method = owner.methods.stream().filter(value -> value.name.equals("inventoryTick")
                && value.desc.equals(SilentGearEffectShape.TICK)).findFirst().orElse(null);
        if (!owner.name.equals(OWNER) || method == null || !method.tryCatchBlocks.isEmpty()
                || (method.access & (ACC_STATIC | ACC_SYNCHRONIZED | ACC_NATIVE | ACC_ABSTRACT)) != 0) {
            return false;
        }

        var code = new ArrayList<AbstractInsnNode>();
        for (var node : method.instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != NOP) {
                code.add(node);
            }
        }
        if (code.size() != 24 || !(code.get(4) instanceof VarInsnNode actor) || actor.getOpcode() != ASTORE
                || !(code.get(10) instanceof VarInsnNode iterator) || iterator.getOpcode() != ASTORE
                || !(code.get(17) instanceof VarInsnNode effect) || effect.getOpcode() != ASTORE
                || actor.var < 5 || iterator.var < 5 || effect.var < 5
                || actor.var == iterator.var || actor.var == effect.var || iterator.var == effect.var) {
            return false;
        }

        return load(code.get(0), ILOAD, 4) && jump(code, 1, IFEQ, 23)
                && load(code.get(2), ALOAD, 1)
                && call(code.get(3), INVOKEVIRTUAL, SilentGearEffectShape.CONTEXT, "player", "()L" + PLAYER + ";")
                && load(code.get(5), ALOAD, actor.var) && jump(code, 6, IFNULL, 23)
                && load(code.get(7), ALOAD, 0)
                && code.get(8) instanceof FieldInsnNode field && field.getOpcode() == GETFIELD
                && field.owner.equals(OWNER) && field.name.equals("effects") && field.desc.equals("Ljava/util/List;")
                && call(code.get(9), INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;")
                && load(code.get(11), ALOAD, iterator.var)
                && call(code.get(12), INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z") && jump(code, 13, IFEQ, 23)
                && load(code.get(14), ALOAD, iterator.var)
                && call(code.get(15), INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;")
                && code.get(16) instanceof TypeInsnNode cast && cast.getOpcode() == CHECKCAST && cast.desc.equals("net/minecraft/core/Holder")
                && load(code.get(18), ALOAD, actor.var) && load(code.get(19), ALOAD, effect.var)
                && call(code.get(20), INVOKEVIRTUAL, PLAYER, "removeEffect", "(Lnet/minecraft/core/Holder;)Z")
                && code.get(21).getOpcode() == POP && jump(code, 22, GOTO, 11) && code.get(23).getOpcode() == RETURN;
    }

    private static boolean load(AbstractInsnNode node, int opcode, int variable) {
        return node instanceof VarInsnNode value && value.getOpcode() == opcode && value.var == variable;
    }

    private static boolean call(AbstractInsnNode node, int opcode, String owner, String name, String desc) {
        return node instanceof MethodInsnNode value && value.getOpcode() == opcode
                && value.owner.equals(owner) && value.name.equals(name) && value.desc.equals(desc);
    }

    private static boolean jump(List<AbstractInsnNode> code, int index, int opcode, int destination) {
        if (!(code.get(index) instanceof JumpInsnNode jump) || jump.getOpcode() != opcode) {
            return false;
        }
        AbstractInsnNode target = jump.label;
        while (target != null && (target.getOpcode() < 0 || target.getOpcode() == NOP)) {
            target = target.getNext();
        }
        return target == code.get(destination);
    }
}
