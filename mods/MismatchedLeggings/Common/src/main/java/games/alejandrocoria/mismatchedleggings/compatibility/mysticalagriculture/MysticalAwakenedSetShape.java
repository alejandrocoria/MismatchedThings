package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Final-export edit of one equipped-slot comparison and its boolean consumer. */
public final class MysticalAwakenedSetShape implements Opcodes {
    public static final String OWNER = "com/blakebr0/mysticalagriculture/item/armor/EssenceChestplateItem";
    public static final String PROOF = "mismatchedleggings$maAwakenedSetReady";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String ITEMS = "com/blakebr0/mysticalagriculture/init/ModItems";
    private static final String DEFERRED = "net/neoforged/neoforge/registries/DeferredHolder";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedleggings/compatibility/mysticalagriculture/MysticalAwakenedSetCompatibility";

    private MysticalAwakenedSetShape() {}

    public static boolean apply(ClassNode owner) {
        if (!owner.name.equals(OWNER)) { return false; }
        if (owner.methods.stream().anyMatch(m -> m.name.equals(PROOF))) { return false; }
        var methods = owner.methods.stream().filter(m -> m.name.equals("hasAwakenedSupremiumSet")
                && m.desc.equals("(L" + PLAYER + ";)Z")).toList();
        if (methods.size() != 1) { return false; }
        var method = methods.getFirst();
        if ((method.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != ACC_STATIC) { return false; }
        var code = code(method);
        if (code.stream().anyMatch(n -> n instanceof MethodInsnNode c && c.owner.equals(ADAPTER))
                || code.stream().anyMatch(n -> n instanceof VarInsnNode v && v.var == 0 && v.getOpcode() != ALOAD)) {
            return false;
        }
        var reads = new ArrayList<Integer>();
        for (int i = 0; i < code.size(); i++) {
            // Unrelated reads of this slot are admissible; ambiguity concerns the native item predicate.
            if (field(code.get(i), SLOT, "LEGS", "L" + SLOT + ";") && i + 2 < code.size()
                    && code.get(i + 2) instanceof FieldInsnNode item && item.owner.equals(ITEMS)
                    && item.name.equals("AWAKENED_SUPREMIUM_LEGGINGS")) { reads.add(i); }
        }
        if (reads.size() != 1) { return false; }
        int slot = reads.getFirst();
        if (slot < 1 || slot + 4 >= code.size() || !variable(code.get(slot - 1), ALOAD, 0)
                || !call(code.get(slot + 1), PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")
                || !(code.get(slot + 2) instanceof FieldInsnNode item)
                || item.getOpcode() != GETSTATIC || !item.owner.equals(ITEMS) || !item.name.equals("AWAKENED_SUPREMIUM_LEGGINGS")) {
            return false;
        }
        int comparison = slot + 3;
        if (item.desc.equals("L" + DEFERRED + ";")) {
            if (comparison + 2 >= code.size()
                    || !call(code.get(comparison++), DEFERRED, "get", "()Ljava/lang/Object;")
                    || !(code.get(comparison++) instanceof TypeInsnNode cast) || cast.getOpcode() != CHECKCAST || !cast.desc.equals(ITEM)) {
                return false;
            }
        } else if (item.desc.equals("L" + ITEM + ";")) {
            // The Fabric runtime may specialize the port's erased is(Object) call to its holder overload.
            if (call(code.get(comparison), ITEM, "builtInRegistryHolder", "()Lnet/minecraft/core/Holder$Reference;")) { comparison++; }
        } else { return false; }
        if (comparison + 1 >= code.size() || !(code.get(comparison) instanceof MethodInsnNode compare)
                || !(call(compare, STACK, "is", "(Ljava/lang/Object;)Z")
                     || item.desc.equals("L" + DEFERRED + ";") && call(compare, STACK, "is", "(L" + ITEM + ";)Z")
                     || item.desc.equals("L" + ITEM + ";") && comparison == slot + 4
                        && call(compare, STACK, "is", "(Lnet/minecraft/core/Holder;)Z"))
                || !(code.get(comparison + 1) instanceof VarInsnNode store) || store.getOpcode() != ISTORE || store.var == 0) {
            return false;
        }
        // Require exactly one definition and one false-branch consumer of this local.
        int definitions = 0;
        int uses = 0;
        for (int i = 0; i < code.size(); i++) {
            var n = code.get(i);
            if (n instanceof IincInsnNode inc && inc.var == store.var) { return false; }
            if (!(n instanceof VarInsnNode v) || v.var != store.var) { continue; }
            if (v == store) { definitions++; continue; }
            if (v.getOpcode() != ILOAD || i <= comparison + 1 || i + 1 >= code.size()
                    || !(code.get(i + 1) instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ
                    || next(branch.label) == null || next(branch.label).getOpcode() != ICONST_0
                    || next(next(branch.label)) == null || next(next(branch.label)).getOpcode() != IRETURN) {
                return false;
            }
            uses++;
        }
        if (definitions != 1 || uses != 1) { return false; }
        // Keep the actual equipped stack and the native result. No identity alias or equipment swap.
        method.instructions.insertBefore(item, new InsnNode(DUP));
        method.instructions.insert(compare, new MethodInsnNode(INVOKESTATIC, ADAPTER, "recognize", "(L" + STACK + ";Z)Z", false));
        method.maxStack++;
        var proof = new MethodNode(ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, PROOF, "()Z", null, null);
        proof.instructions.add(new InsnNode(ICONST_1));
        proof.instructions.add(new InsnNode(IRETURN));
        proof.maxStack = 1;
        owner.methods.add(proof);
        return true;
    }

    private static boolean variable(AbstractInsnNode n, int opcode, int local) {
        return n instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == local;
    }
    private static boolean field(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof FieldInsnNode f && f.getOpcode() == GETSTATIC && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }
    private static boolean call(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof MethodInsnNode c && c.getOpcode() == INVOKEVIRTUAL && !c.itf
                && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc);
    }
    private static List<AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var n : method.instructions) { if (n.getOpcode() >= 0 && n.getOpcode() != NOP) { result.add(n); } }
        return result;
    }
    private static AbstractInsnNode next(AbstractInsnNode n) {
        do { n = n.getNext(); } while (n != null && (n.getOpcode() < 0 || n.getOpcode() == NOP));
        return n;
    }
}
