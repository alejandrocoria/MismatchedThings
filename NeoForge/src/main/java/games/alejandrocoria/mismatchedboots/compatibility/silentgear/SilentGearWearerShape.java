package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Local slot/stack/trait producer-consumer proof, shared by the opt-in candidate and later routing. */
public final class SilentGearWearerShape implements Opcodes {
    static final String OWNER = SilentGearEffectShape.PREFIX + "gear/trait/effect/WielderEffectTraitEffect";
    static final String ARMOR = SilentGearEffectShape.PREFIX + "api/item/GearArmor";
    static final String STACK = SilentGearEffectShape.STACK, CONTEXT = SilentGearEffectShape.CONTEXT;
    static final String TRAIT = SilentGearEffectShape.PREFIX + "gear/trait/Trait";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot", PLAYER = "net/minecraft/world/entity/player/Player";
    static final String COUNT = "(L" + CONTEXT + ";L" + SilentGearEffectShape.PREFIX + "api/item/GearType;L" + PLAYER + ";)I";
    static final String HAS = "(Lnet/minecraft/world/item/ItemInstance;L" + TRAIT + ";)Z";
    record Pair(AbstractInsnNode type, AbstractInsnNode trait) {}

    private SilentGearWearerShape() {}

    public static void publish(ClassNode node) {
        if (node.name.equals(OWNER)) { SilentGearEffectShape.publish(node, "mismatchedboots$sgSetReady", set(node)); }
    }

    static Pair pair(String descriptor, InsnList instructions) {
        if (!COUNT.equals(descriptor)) { return null; }
        return pair(code(instructions), false);
    }

    private static Pair pair(List<AbstractInsnNode> code, boolean transformed) {
        Pair found = null;
        // A single coherent slice, not complete-method equality. Metadata/NOP edits are ignored.
        for (int i = 10; i < code.size(); i++) {
            if (!(code.get(i) instanceof TypeInsnNode type) || type.getOpcode() != INSTANCEOF || !type.desc.equals(ARMOR)
                    || !load(code.get(i - 10), ALOAD, 5) || !call(code.get(i - 9), "java/util/Iterator", "next", "()Ljava/lang/Object;")
                    || !(code.get(i - 8) instanceof TypeInsnNode slot) || slot.getOpcode() != CHECKCAST || !slot.desc.equals(SLOT)
                    || !load(code.get(i - 7), ASTORE, 6)
                    || !load(code.get(i - 6), ALOAD, 3) || !load(code.get(i - 5), ALOAD, 6)
                    || !call(code.get(i - 4), PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")
                    || !load(code.get(i - 3), ASTORE, 7) || !load(code.get(i - 2), ALOAD, 7)
                    || !call(code.get(i - 1), STACK, "getItem", "()Lnet/minecraft/world/item/Item;")) { continue; }
            int cursor = i + 1;
            cursor = handlers(code, cursor, "mismatchedboots$sgSetType", transformed);
            if (cursor < 0 || cursor + 5 >= code.size() || !(code.get(cursor) instanceof JumpInsnNode typeNo) || typeNo.getOpcode() != IFEQ) { continue; }
            if (!load(code.get(cursor + 1), ALOAD, 7) || !load(code.get(cursor + 2), ALOAD, 1)
                    || !call(code.get(cursor + 3), CONTEXT, "trait", "()L" + TRAIT + ";")
                    || !call(code.get(cursor + 4), SilentGearEffectShape.PREFIX + "util/TraitHelper", "hasTrait", HAS)) { continue; }
            AbstractInsnNode trait = code.get(cursor + 4);
            cursor += 5;
            cursor = handlers(code, cursor, "mismatchedboots$sgSetTrait", transformed);
            if (cursor < 0 || cursor + 2 >= code.size() || !(code.get(cursor) instanceof JumpInsnNode traitNo)
                    || traitNo.getOpcode() != IFEQ || typeNo.label != traitNo.label
                    || !(code.get(cursor + 1) instanceof IincInsnNode increment) || increment.var != 4 || increment.incr != 1
                    || !(code.get(cursor + 2) instanceof JumpInsnNode back) || back.getOpcode() != GOTO
                    || nextCode(typeNo.label) != back || !load(nextCode(back.label), ALOAD, 5)) { continue; }
            if (found != null) { return null; }
            found = new Pair(type, trait);
        }
        return found;
    }

    private static int handlers(List<AbstractInsnNode> code, int index, String name, boolean required) {
        int own = 0;
        while (index + 7 < code.size() && load(code.get(index), ALOAD, 0) && code.get(index + 1).getOpcode() == SWAP) {
            // Compose compatible result extensions only when they consume the same slot/stack/context.
            if (!load(code.get(index + 2), ALOAD, 1) || !load(code.get(index + 3), ALOAD, 2)
                    || !load(code.get(index + 4), ALOAD, 3) || !load(code.get(index + 5), ALOAD, 6)
                    || !load(code.get(index + 6), ALOAD, 7)
                    || !(code.get(index + 7) instanceof MethodInsnNode call) || !call.owner.equals(OWNER)
                    || !call.desc.equals("(ZLjava/lang/Object;Ljava/lang/Object;L" + PLAYER + ";L" + SLOT + ";L" + STACK + ";)Z")) { return -1; }
            if (call.name.contains(name)) { own++; }
            index += 8;
        }
        return own > 1 || required && own != 1 ? -1 : index;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && (node.getOpcode() < 0 || node.getOpcode() == NOP));
        return node;
    }

    static boolean set(ClassNode owner) {
        var methods = owner.methods.stream().filter(method -> method.name.equals("getSetPieceCount") && method.desc.equals(COUNT)).toList();
        return methods.size() == 1 && methods.getFirst().tryCatchBlocks.isEmpty() && pair(code(methods.getFirst().instructions), true) != null;
    }

    private static List<AbstractInsnNode> code(InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var node : instructions) { if (node.getOpcode() >= 0 && node.getOpcode() != NOP) { result.add(node); } }
        return result;
    }
    private static boolean load(AbstractInsnNode node, int opcode, int index) { return node instanceof VarInsnNode var && var.getOpcode() == opcode && var.var == index; }
    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }
}
