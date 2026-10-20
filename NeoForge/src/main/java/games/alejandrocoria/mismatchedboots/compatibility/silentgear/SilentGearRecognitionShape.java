package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Returned native query and local distance consumer boundaries, not complete event/class fingerprints. */
final class SilentGearRecognitionShape implements Opcodes {
    static final String PREFIX = SilentGearEffectShape.PREFIX;
    static final String ARMOR = PREFIX + "item/gear/GearArmorItem", EVENTS = PREFIX + "event/GearEvents";
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/LivingFallEvent";
    static final String RESOURCE = "L" + PREFIX + "api/util/DataResource;";
    static final String QUERY = "(Lnet/minecraft/world/entity/player/Player;" + RESOURCE + ")I";
    static final String FALL = "(L" + EVENT + ";)V", RESULT = "(IL" + EVENT + ";)I";
    static final String BRILLIANT = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z";

    private SilentGearRecognitionShape() {}

    static boolean brilliant(ClassNode owner) {
        MethodNode method = method(owner, "makesPiglinsNeutral", BRILLIANT);
        if (method == null || !method.tryCatchBlocks.isEmpty()) { return false; }
        var code = code(method.instructions);
        // The actor is not passed to any native consumer. A new actor call/write is not a read-only query.
        return code.size() == 4 && load(code.get(0), ALOAD, 1) && resource(code.get(1), "BRILLIANT")
                && call(code.get(2), PREFIX + "util/TraitHelper", "hasTrait", "(Lnet/minecraft/world/item/ItemInstance;" + RESOURCE + ")Z")
                && code.get(3).getOpcode() == IRETURN;
    }

    static MethodInsnNode moonQuery(String descriptor, InsnList instructions) {
        if (!FALL.equals(descriptor)) { return null; }
        var code = code(instructions); MethodInsnNode found = null;
        for (int i = 2; i < code.size(); i++) {
            if (load(code.get(i - 2), ALOAD, 2) && resource(code.get(i - 1), "MOONWALKER")
                    && call(code.get(i), PREFIX + "util/TraitHelper", "getHighestLevelArmorOrCurio", QUERY)) {
                if (found != null) { return null; }
                found = (MethodInsnNode) code.get(i);
            }
        }
        return found;
    }

    static boolean moonwalker(ClassNode owner) {
        MethodNode method = method(owner, "onLivingFall", FALL);
        if (method == null) { return false; }
        MethodInsnNode query = moonQuery(method.desc, method.instructions);
        if (query == null) { return false; }
        var code = code(method.instructions);
        int index = code.indexOf(query) + 1, own = 0;
        while (index + 1 < code.size() && load(code.get(index), ALOAD, 0)
                && code.get(index + 1) instanceof MethodInsnNode hook && hook.getOpcode() == INVOKESTATIC
                && hook.owner.equals(EVENTS) && hook.desc.equals(RESULT)) {
            if (hook.name.contains("mismatchedboots$sgMoonLevel")) {
                if (!routing(owner, hook)) { return false; }
                own++;
            }
            index += 2;
        }
        if (own != 1 || index + 17 >= code.size()) { return false; }
        return load(code.get(index), ISTORE, 3) && load(code.get(index + 1), ILOAD, 3)
                && code.get(index + 2) instanceof JumpInsnNode skip && skip.getOpcode() == IFLE
                && code.get(index + 3).getOpcode() == FCONST_1 && load(code.get(index + 4), ILOAD, 3)
                && code.get(index + 5).getOpcode() == I2F && code.get(index + 6) instanceof LdcInsnNode factor
                && factor.cst instanceof Float value && Float.isFinite(value)
                && code.get(index + 7).getOpcode() == FMUL && code.get(index + 8).getOpcode() == FADD
                && load(code.get(index + 9), FSTORE, 4) && load(code.get(index + 10), ALOAD, 0) && load(code.get(index + 11), ALOAD, 0)
                && call(code.get(index + 12), EVENT, "getDistance", "()D") && load(code.get(index + 13), FLOAD, 4)
                && code.get(index + 14).getOpcode() == F2D && code.get(index + 15).getOpcode() == DMUL
                && call(code.get(index + 16), EVENT, "setDistance", "(D)V")
                && method.instructions.indexOf(skip.label) > method.instructions.indexOf(code.get(index + 16));
    }

    private static boolean routing(ClassNode owner, MethodInsnNode hook) {
        MethodNode method = method(owner, hook.name, hook.desc);
        if (method == null || !method.tryCatchBlocks.isEmpty()) { return false; }
        var code = code(method.instructions);
        return code.size() == 5 && load(code.get(0), ILOAD, 0) && load(code.get(1), ALOAD, 1)
                && call(code.get(2), EVENTS, "mismatchedboots$sgMoonReady", "()Z")
                && call(code.get(3), "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearRecognition", "moonwalker", "(IL" + EVENT + ";Z)I")
                && code.get(4).getOpcode() == IRETURN;
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        var methods = owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }
    private static List<AbstractInsnNode> code(InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var node : instructions) { if (node.getOpcode() >= 0 && node.getOpcode() != NOP) { result.add(node); } }
        return result;
    }
    private static boolean load(AbstractInsnNode node, int opcode, int index) { return node instanceof VarInsnNode var && var.getOpcode() == opcode && var.var == index; }
    private static boolean resource(AbstractInsnNode node, String name) {
        return node instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC && field.owner.equals(PREFIX + "util/Const$Traits") && field.name.equals(name) && field.desc.equals(RESOURCE);
    }
    private static boolean call(AbstractInsnNode node, String owner, String name, String desc) {
        return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc);
    }
}
