package games.alejandrocoria.mismatchedleggings.spike.silentgear;

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

/** Research copy of Boots' local paired predicate grammar, not a production admission policy. */
final class SetShape implements Opcodes {
    static final String PREFIX = "net/silentchaos512/gear/";
    static final String OWNER = PREFIX + "gear/trait/effect/WielderEffectTraitEffect";
    static final String CONTEXT = PREFIX + "api/traits/TraitActionContext";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String PLAYER = "net/minecraft/world/entity/player/Player";
    static final String CALLBACK = "(ZLjava/lang/Object;Ljava/lang/Object;L" + PLAYER + ";L" + SLOT + ";L" + STACK + ";)Z";
    static final String COUNT = "(L" + CONTEXT + ";L" + PREFIX + "api/item/GearType;L" + PLAYER + ";)I";
    static final String CANDIDATE = "games/alejandrocoria/mismatchedleggings/spike/silentgear/Candidate";

    record Pair(AbstractInsnNode type, AbstractInsnNode trait) {}

    static Pair pair(String descriptor, InsnList instructions, boolean finalGraph) {
        if (!COUNT.equals(descriptor)) { return null; }
        var code = code(instructions);
        Pair found = null;
        for (int i = 10; i < code.size(); i++) {
            if (!(code.get(i) instanceof TypeInsnNode type) || type.getOpcode() != INSTANCEOF || !type.desc.equals(PREFIX + "api/item/GearArmor")
                    || !load(code.get(i - 10), ALOAD, 5) || !call(code.get(i - 9), "java/util/Iterator", "next", "()Ljava/lang/Object;")
                    || !(code.get(i - 8) instanceof TypeInsnNode slot) || slot.getOpcode() != CHECKCAST || !slot.desc.equals(SLOT)
                    || !load(code.get(i - 7), ASTORE, 6) || !load(code.get(i - 6), ALOAD, 3) || !load(code.get(i - 5), ALOAD, 6)
                    || !call(code.get(i - 4), PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")
                    || !load(code.get(i - 3), ASTORE, 7) || !load(code.get(i - 2), ALOAD, 7)
                    || !call(code.get(i - 1), STACK, "getItem", "()Lnet/minecraft/world/item/Item;")) { continue; }
            int cursor = handlers(code, i + 1, "leggings$sgSpikeType", finalGraph);
            if (cursor < 0 || cursor + 5 >= code.size() || !(code.get(cursor) instanceof JumpInsnNode noType) || noType.getOpcode() != IFEQ
                    || !load(code.get(cursor + 1), ALOAD, 7) || !load(code.get(cursor + 2), ALOAD, 1)
                    || !call(code.get(cursor + 3), CONTEXT, "trait", "()L" + PREFIX + "gear/trait/Trait;")
                    || !call(code.get(cursor + 4), PREFIX + "util/TraitHelper", "hasTrait", "(Lnet/minecraft/world/item/ItemInstance;L" + PREFIX + "gear/trait/Trait;)Z")) { continue; }
            var trait = code.get(cursor + 4);
            cursor = handlers(code, cursor + 5, "leggings$sgSpikeTrait", finalGraph);
            if (cursor < 0 || cursor + 2 >= code.size() || !(code.get(cursor) instanceof JumpInsnNode noTrait)
                    || noTrait.getOpcode() != IFEQ || noType.label != noTrait.label
                    || !(code.get(cursor + 1) instanceof IincInsnNode inc) || inc.var != 4 || inc.incr != 1
                    || !(code.get(cursor + 2) instanceof JumpInsnNode back) || back.getOpcode() != GOTO
                    || next(noType.label) != back || !load(next(back.label), ALOAD, 5)) { continue; }
            if (found != null) { return null; }
            found = new Pair(type, trait);
        }
        return found;
    }

    static boolean finalGraph(ClassNode node) {
        var methods = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount") && m.desc.equals(COUNT)).toList();
        if (methods.size() != 1 || pair(COUNT, methods.getFirst().instructions, true) == null) { return false; }
        for (String name : List.of("leggings$sgSpikeType", "leggings$sgSpikeTrait")) {
            var handlers = node.methods.stream().filter(m -> m.name.contains(name) && m.desc.equals(CALLBACK)).toList();
            if (handlers.size() != 1 || code(handlers.getFirst().instructions).stream().filter(n -> n instanceof MethodInsnNode call
                    && call.owner.equals(CANDIDATE) && call.name.equals("recognizes")).count() != 1) { return false; }
        }
        return true;
    }

    private static int handlers(List<AbstractInsnNode> code, int cursor, String ownName, boolean required) {
        int own = 0;
        while (cursor + 7 < code.size() && load(code.get(cursor), ALOAD, 0) && code.get(cursor + 1).getOpcode() == SWAP) {
            if (!load(code.get(cursor + 2), ALOAD, 1) || !load(code.get(cursor + 3), ALOAD, 2) || !load(code.get(cursor + 4), ALOAD, 3)
                    || !load(code.get(cursor + 5), ALOAD, 6) || !load(code.get(cursor + 6), ALOAD, 7)
                    || !(code.get(cursor + 7) instanceof MethodInsnNode call) || !call.owner.equals(OWNER) || !call.desc.equals(CALLBACK)) { return -1; }
            if (call.name.contains(ownName)) { own++; }
            cursor += 8;
        }
        return own > 1 || required && own != 1 ? -1 : cursor;
    }

    static List<AbstractInsnNode> code(InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var node : instructions) { if (node.getOpcode() >= 0 && node.getOpcode() != NOP) { result.add(node); } }
        return result;
    }

    private static boolean load(AbstractInsnNode node, int opcode, int index) { return node instanceof VarInsnNode var && var.getOpcode() == opcode && var.var == index; }
    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) { return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor); }
    private static AbstractInsnNode next(AbstractInsnNode node) { do { node = node.getNext(); } while (node != null && (node.getOpcode() < 0 || node.getOpcode() == NOP)); return node; }
}
