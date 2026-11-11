package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Own query arguments and permission forwarding, not a fingerprint of the entire native tick. */
final class SilentGearVoidWardShape implements Opcodes {
    static final String EVENTS = SilentGearEffectShape.PREFIX + "event/GearEvents";
    static final String PRE = "net/neoforged/neoforge/event/tick/PlayerTickEvent$Pre";
    static final String TICK = "(L" + PRE + ";)V";
    static final String RESULT = "(ZL" + PRE + ";)Z";
    private static final String RESOURCE = "L" + SilentGearEffectShape.PREFIX + "api/util/DataResource;";
    private static final String QUERY = "(Lnet/minecraft/world/entity/player/Player;" + RESOURCE + ")Z";

    private SilentGearVoidWardShape() {}

    static MethodInsnNode query(String descriptor, InsnList instructions) {
        if (!TICK.equals(descriptor)) {
            return null;
        }
        var code = code(instructions);
        MethodInsnNode found = null;
        for (int i = 2; i < code.size(); i++) {
            if (!(code.get(i - 2) instanceof VarInsnNode player) || player.getOpcode() != ALOAD
                    || !(code.get(i - 1) instanceof FieldInsnNode trait) || trait.getOpcode() != GETSTATIC
                    || !trait.owner.equals(SilentGearEffectShape.PREFIX + "util/Const$Traits")
                    || !trait.name.equals("VOID_WARD") || !trait.desc.equals(RESOURCE)
                    || !call(code.get(i), SilentGearEffectShape.PREFIX + "util/TraitHelper", "hasTraitArmor", QUERY)) {
                continue;
            }

            // Follow this local's latest definition back to this event, not another player query.
            int definition = i - 3;
            while (definition >= 0 && !load(code.get(definition), ASTORE, player.var)) {
                definition--;
            }
            if (definition < 2 || !load(code.get(definition - 2), ALOAD, 0)
                    || !(code.get(definition - 1) instanceof MethodInsnNode producer)
                    || producer.getOpcode() != INVOKEVIRTUAL || !producer.owner.equals(PRE)
                    || !producer.name.equals("getEntity") || !producer.desc.equals("()Lnet/minecraft/world/entity/player/Player;")) {
                return null;
            }
            if (found != null) {
                return null;
            }
            found = (MethodInsnNode) code.get(i);
        }
        return found;
    }

    static boolean ready(ClassNode owner) {
        var method = method(owner, "onPlayerTick", TICK);
        if (method == null) {
            return false;
        }
        var query = query(method.desc, method.instructions);
        if (query == null) {
            return false;
        }
        var code = code(method.instructions);
        int index = code.indexOf(query) + 1;
        int own = 0;
        // Compatible result extensions may precede or follow ours; each preserves one boolean lane.
        while (index + 1 < code.size() && load(code.get(index), ALOAD, 0)
                && code.get(index + 1) instanceof MethodInsnNode hook && hook.getOpcode() == INVOKESTATIC
                && hook.owner.equals(EVENTS) && hook.desc.equals(RESULT)) {
            if (hook.name.contains("mismatchedleggings$sgVoidWard")) {
                if (!route(owner, hook)) {
                    return false;
                }
                own++;
            }
            index += 2;
        }
        return own == 1 && index < code.size() && code.get(index) instanceof JumpInsnNode branch
                && branch.getOpcode() == IFEQ
                && method.instructions.indexOf(branch.label) > method.instructions.indexOf(branch);
    }

    private static boolean route(ClassNode owner, MethodInsnNode hook) {
        var route = method(owner, hook.name, hook.desc);
        if (route == null || !route.tryCatchBlocks.isEmpty()) {
            return false;
        }
        var body = code(route.instructions);
        return body.size() == 5 && load(body.get(0), ILOAD, 0) && load(body.get(1), ALOAD, 1)
                && call(body.get(2), EVENTS, "mismatchedleggings$sgVoidReady", "()Z")
                && call(body.get(3), "games/alejandrocoria/mismatchedleggings/compatibility/silentgear/SilentGearVoidWard",
                        "recognize", "(ZL" + PRE + ";Z)Z") && body.get(4).getOpcode() == IRETURN;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var methods = owner.methods.stream().filter(value -> value.name.equals(name) && value.desc.equals(descriptor)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }

    private static List<AbstractInsnNode> code(InsnList instructions) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : instructions) {
            if (instruction.getOpcode() >= 0 && instruction.getOpcode() != NOP) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static boolean load(AbstractInsnNode node, int opcode, int variable) {
        return node instanceof VarInsnNode value && value.getOpcode() == opcode && value.var == variable;
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode value && value.getOpcode() == INVOKESTATIC
                && value.owner.equals(owner) && value.name.equals(name) && value.desc.equals(descriptor);
    }
}
