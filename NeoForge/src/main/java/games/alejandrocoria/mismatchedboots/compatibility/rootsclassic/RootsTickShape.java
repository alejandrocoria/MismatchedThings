package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Checks this optional stack-consumer boundary, not whole method equality or arbitrary wrappers. */
final class RootsTickShape implements Opcodes {
    static final String SYLVAN = "elucent/rootsclassic/item/SylvanArmorItem";
    static final String WILDWOOD = "elucent/rootsclassic/item/WildwoodArmorItem";
    static final String UTIL = "elucent/rootsclassic/util/RootsUtil";
    static final String ITEM = "net/minecraft/world/item/Item";
    static final String STACK = ITEM + "Stack";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String LEVEL = "net/minecraft/server/level/ServerLevel";
    static final String TICK = "(L" + STACK + ";L" + LEVEL
            + ";Lnet/minecraft/world/entity/Entity;L" + SLOT + ";)V";
    static final String REPAIR = "(Lnet/minecraft/util/RandomSource;L" + STACK + ";)V";
    static final String ENTRY_PROOF = "mismatchedboots$rootsTickReady";
    static final String REPAIR_PROOF = "mismatchedboots$rootsRepairReady";

    private RootsTickShape() {}

    static boolean tick(ClassNode owner) {
        MethodNode method = method(owner, "inventoryTick", TICK);
        if (!ITEM.equals(owner.superName) || !api(method, false) || writesInputs(method, 1, 4)) { return false; }
        List<AbstractInsnNode> code = code(method);
        int repairs = 0, stackLoads = 0;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode node = code.get(i);
            if (var(node, ALOAD, 1)) {
                stackLoads++;
                if (i + 1 >= code.size() || !call(code.get(i + 1), INVOKESTATIC, UTIL, "randomlyRepair", REPAIR)) {
                    return false; // No cast, alias, snapshot, item query or unknown stack consumer is safe here.
                }
            }
            if (!call(node, INVOKESTATIC, UTIL, "randomlyRepair", REPAIR)) { continue; }
            repairs++;
            if (i < 9 || !var(code.get(i - 9), ALOAD, 4)
                    || !(code.get(i - 8) instanceof JumpInsnNode absent) || absent.getOpcode() != IFNULL
                    || !var(code.get(i - 7), ALOAD, 4)
                    || !call(code.get(i - 6), INVOKEVIRTUAL, SLOT, "getIndex", "()I")
                    || integer(code.get(i - 5)) != 4
                    || !(code.get(i - 4) instanceof JumpInsnNode outside) || outside.getOpcode() != IF_ICMPGE
                    || next(absent.label) != next(outside.label) || !returns(absent)
                    || !var(code.get(i - 3), ALOAD, 2)
                    || !call(code.get(i - 2), INVOKEVIRTUAL, LEVEL, "getRandom", "()Lnet/minecraft/util/RandomSource;")
                    || !var(code.get(i - 1), ALOAD, 1)) { return false; }
        }
        return repairs == 1 && stackLoads == 1;
    }

    static boolean repair(ClassNode owner) {
        MethodNode method = method(owner, "randomlyRepair", REPAIR);
        if (!api(method, true) || writesInputs(method, 0, 1)) { return false; }
        List<AbstractInsnNode> code = code(method);
        int writes = 0, damaged = 0, stackLoads = 0;
        for (int i = 0; i < code.size(); i++) {
            AbstractInsnNode node = code.get(i);
            if (var(node, ALOAD, 1)) {
                stackLoads++;
                if (i + 1 >= code.size()) { return false; }
                AbstractInsnNode consumer = code.get(i + 1);
                boolean read = call(consumer, INVOKEVIRTUAL, STACK, "isDamaged", "()Z")
                        || call(consumer, INVOKEVIRTUAL, STACK, "getDamageValue", "()I");
                boolean destination = var(consumer, ALOAD, 1) && i + 2 < code.size()
                        && call(code.get(i + 2), INVOKEVIRTUAL, STACK, "getDamageValue", "()I");
                if (!read && !destination) { return false; }
            }
            if (call(node, INVOKEVIRTUAL, STACK, "isDamaged", "()Z")) {
                damaged++;
                if (i == 0 || !var(code.get(i - 1), ALOAD, 1) || i + 1 >= code.size()
                        || !(code.get(i + 1) instanceof JumpInsnNode healthy) || healthy.getOpcode() != IFEQ
                        || !returns(healthy)) { return false; }
            }
            if (!call(node, INVOKEVIRTUAL, STACK, "setDamageValue", "(I)V")) { continue; }
            writes++;
            if (i < 6 || !(code.get(i - 6) instanceof JumpInsnNode miss) || miss.getOpcode() != IFNE
                    || !returns(miss)
                    || !var(code.get(i - 5), ALOAD, 1) || !var(code.get(i - 4), ALOAD, 1)
                    || !call(code.get(i - 3), INVOKEVIRTUAL, STACK, "getDamageValue", "()I")
                    || integer(code.get(i - 2)) <= 0 || code.get(i - 1).getOpcode() != ISUB) { return false; }
        }
        return writes == 1 && damaged == 1 && stackLoads == 3;
    }

    static void publish(ClassNode owner, String getter, boolean isStatic, boolean valid) {
        MethodNode method = method(owner, getter, "()Z");
        if (method == null) { return; }
        int access = ACC_PUBLIC | (isStatic ? ACC_STATIC : 0);
        List<AbstractInsnNode> code = code(method);
        int initialAccess = isStatic ? ACC_PRIVATE | ACC_STATIC : ACC_PUBLIC;
        boolean untouched = method.access == initialAccess && method.tryCatchBlocks.isEmpty()
                && code.size() == 2 && code.getFirst().getOpcode() == ICONST_0 && code.getLast().getOpcode() == IRETURN;
        // A changed proof cannot retain a stale true value. It is permission, not an eligibility callback.
        method.access = access;
        method.instructions.clear();
        method.instructions.add(new InsnNode(untouched && valid ? ICONST_1 : ICONST_0));
        method.instructions.add(new InsnNode(IRETURN));
        method.tryCatchBlocks.clear(); method.localVariables = null;
        method.visibleLocalVariableAnnotations = null; method.invisibleLocalVariableAnnotations = null;
        method.maxStack = Math.max(method.maxStack, 1);
        method.maxLocals = isStatic ? 0 : 1;
    }

    private static boolean api(MethodNode method, boolean isStatic) {
        return method != null && (method.access & (ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE))
                == (ACC_PUBLIC | (isStatic ? ACC_STATIC : 0)) && method.tryCatchBlocks.isEmpty();
    }

    private static boolean writesInputs(MethodNode method, int first, int last) {
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof VarInsnNode variable && variable.var >= first && variable.var <= last
                    && variable.getOpcode() >= ISTORE && variable.getOpcode() <= ASTORE) { return true; }
            if (node instanceof IincInsnNode increment && increment.var >= first && increment.var <= last) { return true; }
        }
        return false;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var matches = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != NOP) { result.add(node); }
        }
        return result;
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && (node.getOpcode() < 0 || node.getOpcode() == NOP));
        return node;
    }

    private static boolean returns(JumpInsnNode branch) {
        AbstractInsnNode target = next(branch.label);
        return target != null && target.getOpcode() == RETURN;
    }

    private static boolean var(AbstractInsnNode node, int opcode, int local) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == local;
    }

    private static boolean call(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor) && !call.itf;
    }

    private static int integer(AbstractInsnNode node) {
        if (node.getOpcode() >= ICONST_M1 && node.getOpcode() <= ICONST_5) { return node.getOpcode() - ICONST_0; }
        if (node instanceof IntInsnNode operand) { return operand.operand; }
        if (node instanceof LdcInsnNode constant && constant.cst instanceof Integer value) { return value; }
        return Integer.MIN_VALUE;
    }
}
