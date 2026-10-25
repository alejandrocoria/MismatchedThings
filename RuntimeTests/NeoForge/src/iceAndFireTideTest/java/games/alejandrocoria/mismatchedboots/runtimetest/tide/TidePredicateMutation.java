package games.alejandrocoria.mismatchedboots.runtimetest.tide;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.HashMap;

/** Adversarial fixture applied to the real loadable class, not an assertion-only copy. */
final class TidePredicateMutation implements Opcodes {
    private static boolean applied;

    static void apply(ClassNode node, String mode) {
        if (applied) {
            throw new AssertionError("Predicate mutation applied twice");
        }
        MethodNode entry = node.methods.stream().filter(method -> method.name.equals("inventoryTick")
                && method.desc.equals(TideFixturePlugin.TICK)).findFirst().orElseThrow();
        for (var instruction : entry.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != GETSTATIC
                    || !field.owner.equals("net/minecraft/world/entity/EquipmentSlot") || !field.name.equals("FEET")) {
                continue;
            }
            AbstractInsnNode type = field;
            while (!(type instanceof TypeInsnNode)) {
                type = type.getNext();
            }
            if (mode.equals("predicateChanged")) {
                field.name = "LEGS"; // The fourth count term reads LEGS a second time.
            } else if (mode.equals("predicateReplaced")) {
                ((TypeInsnNode) type).desc = "net/minecraft/world/item/ItemStack"; // Item cannot be ItemStack: the native FEET result is false.
            } else if (mode.equals("predicateAmbiguous")) {
                AbstractInsnNode start = field.getPrevious();
                while (start.getOpcode() < 0) {
                    start = start.getPrevious();
                }
                InsnList duplicate = new InsnList();
                for (AbstractInsnNode part = start; ; part = part.getNext()) {
                    if (part.getOpcode() >= 0) {
                        duplicate.add(part.clone(new HashMap<>()));
                    }
                    if (part == type) {
                        break;
                    }
                }
                duplicate.add(new InsnNode(POP)); // Extra equipment predicate, no effects or carrier access.
                entry.instructions.insertBefore(start, duplicate);
            } else {
                throw new AssertionError("Unknown predicate mutation: " + mode);
            }
            applied = true;
            return;
        }
        throw new AssertionError("Missing native FEET operation for actual predicate mutation");
    }

    static void verify(String mode) {
        if (mode.startsWith("predicate") && !applied) {
            throw new AssertionError("Runtime predicate mutation did not run");
        }
    }
}
