package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearEffectShapeTest implements Opcodes {
    @Test
    void repairSelectsOnlyTheNegativeLevelScaledCarrierWriteAndToleratesUnrelatedEdits() {
        MethodNode method = repair();
        assertNotNull(SilentGearEffectShape.repairCall(method.desc, method.instructions));
        method.instructions.insert(new InsnNode(NOP));
        assertNotNull(SilentGearEffectShape.repairCall(method.desc, method.instructions));
        for (var node : method.instructions) {
            if (node.getOpcode() == INEG) {
                method.instructions.set(node, new InsnNode(NOP));
                break;
            }
        }
        assertNull(SilentGearEffectShape.repairCall(method.desc, method.instructions));
        MethodNode wrongCarrier = repair();
        for (var node : wrongCarrier.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("gear")) {
                call.name = "otherStack";
            }
        }
        assertNull(SilentGearEffectShape.repairCall(wrongCarrier.desc, wrongCarrier.instructions));
    }

    @Test
    void wearProofIsLocalAndCannotRetainAStalePermissionAfterDeltaChange() {
        var owner = new ClassNode();
        owner.name = SilentGearEffectShape.WEAR;
        var method = new MethodNode(ACC_PUBLIC, "onDurabilityDamage", SilentGearEffectShape.DURABILITY, null, null);
        method.instructions.add(new VarInsnNode(ILOAD, 2));
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, owner.name, "effectScale", "I"));
        var addition = new InsnNode(IADD);
        method.instructions.add(addition);
        method.instructions.add(new InsnNode(IRETURN));
        owner.methods.add(method);
        assertTrue(SilentGearEffectShape.wear(owner));
        method.instructions.insert(new InsnNode(NOP));
        owner.methods.add(new MethodNode(ACC_PRIVATE, "unrelated", "()V", null, null));
        assertTrue(SilentGearEffectShape.wear(owner));
        method.instructions.set(addition, new InsnNode(ISUB));
        assertFalse(SilentGearEffectShape.wear(owner));
        var proof = new MethodNode(ACC_PUBLIC, "mismatchedboots$sgWearReady", "()Z", null, null);
        proof.instructions.add(new InsnNode(ICONST_1));
        proof.instructions.add(new InsnNode(IRETURN));
        owner.methods.add(proof);
        SilentGearEffectShape.publish(owner, proof.name, true);
        assertTrue(proof.instructions.getFirst().getOpcode() == ICONST_0, "A foreign true getter must not bypass proof publication");
    }

    private static MethodNode repair() {
        var method = new MethodNode(ACC_PUBLIC, "inventoryTick", SilentGearEffectShape.TICK, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, SilentGearEffectShape.REPAIR, "repairAmount", "I"));
        method.instructions.add(new InsnNode(INEG));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearEffectShape.CONTEXT, "traitLevel", "()I", false));
        method.instructions.add(new InsnNode(IMUL));
        method.instructions.add(new VarInsnNode(ISTORE, 5));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                                                   SilentGearEffectShape.CONTEXT,
                                                   "gear",
                                                   "()L" + SilentGearEffectShape.STACK + ";",
                                                   false));
        method.instructions.add(new VarInsnNode(ILOAD, 5));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                                                   SilentGearEffectShape.CONTEXT,
                                                   "player",
                                                   "()Lnet/minecraft/world/entity/player/Player;",
                                                   false));
        method.instructions.add(new FieldInsnNode(GETSTATIC,
                                                  "net/minecraft/world/InteractionHand",
                                                  "MAIN_HAND",
                                                  "Lnet/minecraft/world/InteractionHand;"));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                   SilentGearEffectShape.PREFIX + "util/GearHelper",
                                                   "attemptDamage",
                                                   SilentGearEffectShape.DAMAGE,
                                                   false));
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }
}
