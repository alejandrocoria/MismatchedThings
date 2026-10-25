package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Final-export, slot-local edit: no whole-set grammar or dependency on unrelated slot wrappers. */
final class RootsSetShape implements Opcodes {
    static final String STAFF = "elucent/rootsclassic/item/StaffItem";
    static final String CRYSTAL = "elucent/rootsclassic/item/CrystalStaffItem";
    static final String EVENTS = "elucent/rootsclassic/event/ComponentSpellsEvent";
    static final String PLAYER = "net/minecraft/world/entity/player/Player";
    static final String STACK = RootsTickShape.STACK;
    static final String ITEM = RootsTickShape.ITEM;
    static final String SLOT = RootsTickShape.SLOT;
    static final String CAST = "(L" + STACK + ";Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/rootsclassic/RootsSetCompatibility";

    private RootsSetShape() {
    }

    static boolean target(String name) {
        return name.equals(STAFF) || name.equals(CRYSTAL) || name.equals(EVENTS);
    }

    static boolean apply(ClassNode owner) {
        boolean staff = owner.name.equals(STAFF) || owner.name.equals(CRYSTAL);
        String name = staff ? "releaseUsing" : "wildwoodArmorRegenFullset";
        String descriptor = staff ? CAST : "(L" + PLAYER + ";)V";
        var methods = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        if (methods.size() != 1) {
            return false;
        }
        MethodNode method = methods.getFirst();
        if ((method.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return false;
        }
        List<AbstractInsnNode> code = code(method);
        // A prior invocation or competing edit of our hook is never grounds for a second insertion.
        if (code.stream().anyMatch(n -> n instanceof MethodInsnNode call && call.owner.equals(ADAPTER))) {
            return false;
        }
        List<Integer> reads = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            if (field(code.get(i), SLOT, "FEET", "L" + SLOT + ";")) {
                reads.add(i);
            }
        }
        if (reads.size() != 1) {
            return false;
        }
        int feet = reads.getFirst();
        if (feet < 1 || feet + 3 >= code.size() || !(code.get(feet - 1) instanceof VarInsnNode player)
                || player.getOpcode() != ALOAD
                || !call(code.get(feet + 1), PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")) {
            return false;
        }
        if (!playerOrigin(code, feet - 1, player.var, staff)) {
            return false;
        }
        if (staff) {
            if (!call(code.get(feet + 2), STACK, "getItem", "()L" + ITEM + ";")
                    || !(code.get(feet + 3) instanceof TypeInsnNode type) || type.getOpcode() != INSTANCEOF
                    || !type.desc.equals(RootsTickShape.SYLVAN) || feet + 6 >= code.size()
                    || !(code.get(feet + 4) instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ
                    || !(code.get(feet + 5) instanceof IincInsnNode increment) || increment.incr != 1
                    || !potencyLocal(code, increment.var, owner.name.equals(CRYSTAL))
                    || next(branch.label) != code.get(feet + 6)) {
                return false;
            }
            InsnList hook = new InsnList();
            // Keep the exact native FEET stack; do not re-query equipment after evaluating its item.
            method.instructions.insertBefore(code.get(feet + 2), new InsnNode(DUP));
            hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "sylvan", "(L" + STACK + ";Z)Z", false));
            method.instructions.insert(type, hook);
        } else {
            if (!(code.get(feet + 2) instanceof VarInsnNode store) || store.getOpcode() != ASTORE) {
                return false;
            }
            List<Integer> uses = new ArrayList<>();
            for (int i = feet + 3; i < code.size(); i++) {
                if (code.get(i) instanceof VarInsnNode local && local.var == store.var) {
                    if (local.getOpcode() != ALOAD) {
                        return false;
                    }
                    uses.add(i);
                }
            }
            if (uses.size() != 1) {
                return false;
            }
            int load = uses.getFirst();
            if (load + 4 >= code.size() || !call(code.get(load + 1), STACK, "getItem", "()L" + ITEM + ";")
                    || !field(code.get(load + 2), "elucent/rootsclassic/registry/RootsRegistry", "WILDWOOD_BOOTS",
                              "Lnet/neoforged/neoforge/registries/DeferredItem;")
                    || !call(code.get(load + 3), "net/neoforged/neoforge/registries/DeferredItem", "get", "()Ljava/lang/Object;")
                    || !(code.get(load + 4) instanceof JumpInsnNode branch) || branch.getOpcode() != IF_ACMPNE
                    || next(branch.label) == null || next(branch.label).getOpcode() != RETURN
                    || !unwritten(code, feet + 3, load, player.var)) {
                return false;
            }
            // Compare the original operands first. No global getItem alias or equipment exchange.
            InsnList hook = new InsnList();
            method.instructions.insertBefore(code.get(load + 1), new InsnNode(DUP));
            hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "wildwood", "(L" + STACK + ";L" + ITEM + ";Ljava/lang/Object;)Z", false));
            method.instructions.insertBefore(branch, hook);
            method.instructions.set(branch, new JumpInsnNode(IFEQ, branch.label));
        }
        method.maxStack += 1;
        return true;
    }

    private static boolean playerOrigin(List<AbstractInsnNode> code, int end, int player, boolean staff) {
        if (!staff) {
            return player == 1 && unwritten(code, 0, end, player);
        }
        int definitions = 0;
        for (int i = 0; i < end; i++) {
            if (code.get(i) instanceof VarInsnNode local && local.var == player && local.getOpcode() == ASTORE) {
                if (i < 2 || !(code.get(i - 1) instanceof TypeInsnNode cast) || cast.getOpcode() != CHECKCAST
                        || !cast.desc.equals(PLAYER) || !(code.get(i - 2) instanceof VarInsnNode source)
                        || source.getOpcode() != ALOAD || source.var != 3) {
                    return false;
                }
                definitions++;
            }
        }
        return player > 4 && definitions == 1 && unwritten(code, 0, end, 3);
    }

    private static boolean potencyLocal(List<AbstractInsnNode> code, int local, boolean crystal) {
        int definitions = 0;
        for (int i = 0; i < code.size(); i++) {
            if (!(code.get(i) instanceof VarInsnNode store) || store.var != local || store.getOpcode() != ISTORE) {
                continue;
            }
            definitions++;
            int source = crystal ? i - 3 : i - 1;
            if (source < 0 || !call(code.get(source), "elucent/rootsclassic/datacomponent/SpellData", "potency", "()I")
                    || crystal && (code.get(i - 2).getOpcode() != ICONST_1 || code.get(i - 1).getOpcode() != IADD)) {
                return false;
            }
        }
        return definitions == 1;
    }

    private static boolean unwritten(List<AbstractInsnNode> code, int start, int end, int local) {
        for (int i = start; i < end; i++) {
            AbstractInsnNode node = code.get(i);
            if (node instanceof VarInsnNode variable && variable.var == local && variable.getOpcode() >= ISTORE
                    && variable.getOpcode() <= ASTORE || node instanceof IincInsnNode increment && increment.var == local) {
                return false;
            }
        }
        return true;
    }

    private static boolean field(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC && field.owner.equals(owner)
                && field.name.equals(name) && field.desc.equals(descriptor);
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == INVOKEVIRTUAL && !call.itf
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != NOP) {
                result.add(node);
            }
        }
        return result;
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do {
            node = node.getNext();
        } while (node != null && (node.getOpcode() < 0 || node.getOpcode() == NOP));
        return node;
    }
}
