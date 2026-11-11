package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.FADD;
import static org.objectweb.asm.Opcodes.FSUB;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.RETURN;

class SilentGearMagnetShapeTest {
    @Test
    void pinnedCallbackAndQueueHaveTheAdmittedReadOnlyBoundary() throws Exception {
        assertTrue(SilentGearMagnetShape.effect(nativeClass(SilentGearMagnetShape.EFFECT)));
        assertTrue(SilentGearMagnetShape.tracker(nativeClass(SilentGearMagnetShape.TRACKER)));
    }

    @Test
    void harmlessInstructionsAndSiblingReadinessDoNotInvalidateNativeBodies() throws Exception {
        for (String name : new String[]{SilentGearMagnetShape.EFFECT, SilentGearMagnetShape.TRACKER}) {
            var owner = nativeClass(name);
            for (var method : owner.methods) {
                method.instructions.insert(new InsnNode(NOP));
            }
            var peer = new MethodNode(ACC_STATIC, "mismatchedboots$unrelatedReadiness", "()V", null, null);
            peer.instructions.add(new InsnNode(RETURN));
            owner.methods.add(peer);
            assertTrue(name.equals(SilentGearMagnetShape.EFFECT)
                    ? SilentGearMagnetShape.effect(owner) : SilentGearMagnetShape.tracker(owner));
        }
    }

    @Test
    void reachableForeignCallAndChangedRadiusProducerRefuseTheCallback() throws Exception {
        var owner = nativeClass(SilentGearMagnetShape.EFFECT);
        method(owner, "canAffectItem").instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Writer", "mutateGear", "()V", false));
        assertFalse(SilentGearMagnetShape.effect(owner));

        owner = nativeClass(SilentGearMagnetShape.EFFECT);
        var pull = method(owner, "tickMagnet");
        for (var instruction : pull.instructions.toArray()) {
            if (instruction.getOpcode() == FADD) {
                pull.instructions.set(instruction, new InsnNode(FSUB));
                break;
            }
        }
        assertFalse(SilentGearMagnetShape.effect(owner));
    }

    @Test
    void weakerVectorArbitrationAndForeignFlushTargetsRefuseTheQueue() throws Exception {
        var owner = nativeClass(SilentGearMagnetShape.TRACKER);
        for (var instruction : method(owner, "pushItem").instructions) {
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == IFLE) {
                jump.setOpcode(IFGE);
                break;
            }
        }
        assertFalse(SilentGearMagnetShape.tracker(owner));

        owner = nativeClass(SilentGearMagnetShape.TRACKER);
        var publication = owner.methods.stream().filter(m -> m.name.startsWith("lambda$onLevelTick")).findFirst().orElseThrow();
        publication.instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Writer", "mutateGear", "()V", false));
        assertFalse(SilentGearMagnetShape.tracker(owner));
    }

    @Test
    void parameterAdmissionFollowsFiniteNativeRadiusWithoutFreezingStockBalance() {
        assertTrue(SilentGearMagnetApi.parameters(0.06F, 3, 5));
        assertTrue(SilentGearMagnetApi.parameters(0.1F, 4, 7));
        assertFalse(SilentGearMagnetApi.parameters(Float.NaN, 3, 5));
        assertFalse(SilentGearMagnetApi.parameters(0.06F, Float.POSITIVE_INFINITY, 5));
        assertFalse(SilentGearMagnetApi.parameters(0.06F, Float.MAX_VALUE, 5));
        assertFalse(SilentGearMagnetApi.parameters(0.06F, 3, 0));
        assertFalse(SilentGearMagnetApi.parameters(0.06F, 3, Byte.MAX_VALUE + 1));
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (var zip = new ZipFile(System.getProperty("mismatchedleggings.test.sgLiveJar"))) {
            try (var stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
                var node = new ClassNode();
                new ClassReader(stream.readAllBytes()).accept(node, 0);
                return node;
            }
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
}
