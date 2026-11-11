package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearCureShapeTest implements Opcodes {
    @Test
    void pinnedNativeLeafAcceptsRenumberingAndUnrelatedEdits() throws Exception {
        var owner = nativeOwner();
        assertTrue(SilentGearCureShape.admitted(owner));
        var method = tick(owner);
        method.instructions.insert(new InsnNode(NOP));
        for (var instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.var >= 5) {
                variable.var += 4;
            }
        }
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelatedPresentation", "()V", null, null));
        assertTrue(SilentGearCureShape.admitted(owner));
    }

    @Test
    void changedSelectionActorTargetConsumerAndLoopRefuseTheLeaf() throws Exception {
        for (String fault : new String[]{"selected", "actor", "targets", "consumer", "exit", "loop"}) {
            var owner = nativeOwner();
            var code = tick(owner).instructions;
            for (var instruction : code) {
                if (fault.equals("selected") && instruction instanceof VarInsnNode selected && selected.getOpcode() == ILOAD) {
                    selected.var = 2;
                    break;
                }
                if (instruction instanceof MethodInsnNode call) {
                    if (fault.equals("actor") && call.name.equals("player")) { call.name = "otherActor"; }
                    if (fault.equals("consumer") && call.name.equals("removeEffect")) { call.name = "unknownWriter"; }
                }
                if (fault.equals("targets") && instruction instanceof FieldInsnNode field) {
                    field.name = "otherEffects";
                }
                if (fault.equals("exit") && instruction instanceof JumpInsnNode exit && exit.getOpcode() == IFEQ) {
                    exit.setOpcode(IFNE);
                    break;
                }
                if (fault.equals("loop") && instruction instanceof JumpInsnNode jump && jump.getOpcode() == GOTO) {
                    var exit = java.util.Arrays.stream(code.toArray()).filter(JumpInsnNode.class::isInstance)
                            .map(JumpInsnNode.class::cast).findFirst().orElseThrow();
                    jump.label = exit.label;
                }
            }
            assertFalse(SilentGearCureShape.admitted(owner), fault);
        }
    }

    @Test
    void additionalGearWorkAndMissingCancellationAreNotAuditedLeaves() throws Exception {
        var owner = nativeOwner();
        tick(owner).instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/GearWriter", "recalculate", "()V", false));
        assertFalse(SilentGearCureShape.admitted(owner));
        owner = nativeOwner();
        tick(owner).instructions.clear();
        tick(owner).instructions.add(new InsnNode(RETURN));
        assertFalse(SilentGearCureShape.admitted(owner));
    }

    private static ClassNode nativeOwner() throws Exception {
        try (var zip = new ZipFile(System.getProperty("mismatchedleggings.test.sgLiveJar"));
             var input = zip.getInputStream(zip.getEntry(SilentGearCureShape.OWNER + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode tick(ClassNode owner) {
        return owner.methods.stream().filter(m -> m.name.equals("inventoryTick") && m.desc.equals(SilentGearEffectShape.TICK))
                .findFirst().orElseThrow();
    }
}
