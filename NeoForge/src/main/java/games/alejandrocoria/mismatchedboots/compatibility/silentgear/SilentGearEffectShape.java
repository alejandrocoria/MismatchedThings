package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Local delta producers/consumer gates, not equality of complete native methods. */
final class SilentGearEffectShape implements Opcodes {
    static final String PREFIX = "net/silentchaos512/gear/";
    static final String REPAIR = PREFIX + "gear/trait/effect/SelfRepairTraitEffect";
    static final String WEAR = PREFIX + "gear/trait/effect/DurabilityTraitEffect";
    static final String CONTEXT = PREFIX + "api/traits/TraitActionContext";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String DAMAGE = "(L" + STACK + ";ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)V";
    static final String TICK = "(L" + CONTEXT + ";Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Z)V";
    static final String DURABILITY = "(L" + CONTEXT + ";I)I";

    private SilentGearEffectShape() {}

    static MethodInsnNode repairCall(String descriptor, InsnList instructions) {
        if (!TICK.equals(descriptor)) { return null; }
        List<AbstractInsnNode> code = code(instructions);
        MethodInsnNode selected = null;
        for (int i = 13; i < code.size(); i++) {
            if (!damage(code.get(i)) || !field(code.get(i - 1), GETSTATIC, "net/minecraft/world/InteractionHand", "MAIN_HAND")
                    || !call(code.get(i - 2), CONTEXT, "player", "()Lnet/minecraft/world/entity/player/Player;") || !load(code.get(i - 3), ALOAD, 1)
                    || !(code.get(i - 4) instanceof VarInsnNode amount) || amount.getOpcode() != ILOAD
                    || !call(code.get(i - 5), CONTEXT, "gear", "()L" + STACK + ";") || !load(code.get(i - 6), ALOAD, 1)
                    || !load(code.get(i - 7), ISTORE, amount.var) || code.get(i - 8).getOpcode() != IMUL
                    || !call(code.get(i - 9), CONTEXT, "traitLevel", "()I") || !load(code.get(i - 10), ALOAD, 1)
                    || code.get(i - 11).getOpcode() != INEG || !field(code.get(i - 12), GETFIELD, REPAIR, "repairAmount")
                    || !load(code.get(i - 13), ALOAD, 0)) { continue; }
            if (selected != null) { return null; }
            selected = (MethodInsnNode) code.get(i);
        }
        return selected;
    }

    static boolean repair(ClassNode owner) {
        MethodNode method = method(owner, "inventoryTick", TICK);
        if (method == null) { return false; }
        var code = code(method.instructions);
        List<AbstractInsnNode> writes = code.stream().filter(SilentGearEffectShape::damage).toList();
        if (writes.size() != 1) { return false; }
        int write = code.indexOf(writes.getFirst()), guards = 0;
        for (int i = 0; i + 1 < write; i++) {
            if (!(code.get(i) instanceof MethodInsnNode call) || !call.owner.equals(owner.name)
                    || !call.name.contains("mismatchedboots$sgRepairCondition") || !call.desc.endsWith(")Z")) { continue; }
            if (!(code.get(i + 1) instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ
                    || method.instructions.indexOf(branch.label) <= method.instructions.indexOf(writes.getFirst())) { return false; }
            MethodNode handler = method(owner, call.name, call.desc);
            if (handler == null || code(handler.instructions).stream().filter(node -> call(node,
                    "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearEffects", "captureRepair",
                    "(L" + STACK + ";ILnet/minecraft/world/entity/LivingEntity;)Z")).count() != 1) { return false; }
            guards++;
        }
        return guards == 1;
    }

    static boolean wear(ClassNode owner) {
        MethodNode method = method(owner, "onDurabilityDamage", DURABILITY);
        if (method == null) { return false; }
        var code = code(method.instructions);
        int returns = 0;
        for (int i = 4; i < code.size(); i++) {
            if (code.get(i).getOpcode() == IRETURN && code.get(i - 1).getOpcode() == IADD
                    && field(code.get(i - 2), GETFIELD, WEAR, "effectScale") && load(code.get(i - 3), ALOAD, 0)
                    && load(code.get(i - 4), ILOAD, 2)) { returns++; }
        }
        return returns == 1;
    }

    static void publish(ClassNode owner, String getter, boolean ready) {
        MethodNode proof = method(owner, getter, "()Z");
        if (proof == null) { return; }
        var original = code(proof.instructions);
        boolean untouched = original.size() == 2 && original.getFirst().getOpcode() == ICONST_0
                && original.getLast().getOpcode() == IRETURN && proof.tryCatchBlocks.isEmpty();
        proof.instructions.clear(); proof.tryCatchBlocks.clear(); proof.localVariables = null;
        proof.visibleLocalVariableAnnotations = null; proof.invisibleLocalVariableAnnotations = null;
        proof.instructions.add(new InsnNode(ready && untouched ? ICONST_1 : ICONST_0));
        proof.instructions.add(new InsnNode(IRETURN)); proof.maxStack = 1; proof.maxLocals = 1;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static List<AbstractInsnNode> code(InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var node : instructions) { if (node.getOpcode() >= 0 && node.getOpcode() != NOP) { result.add(node); } }
        return result;
    }

    private static boolean damage(AbstractInsnNode node) { return call(node, PREFIX + "util/GearHelper", "attemptDamage", DAMAGE); }
    private static boolean load(AbstractInsnNode node, int opcode, int variable) {
        return node instanceof VarInsnNode instruction && instruction.getOpcode() == opcode && instruction.var == variable;
    }
    private static boolean field(AbstractInsnNode node, int opcode, String owner, String name) {
        return node instanceof FieldInsnNode instruction && instruction.getOpcode() == opcode && instruction.owner.equals(owner) && instruction.name.equals(name);
    }
    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode instruction && instruction.owner.equals(owner) && instruction.name.equals(name) && instruction.desc.equals(descriptor);
    }
}
