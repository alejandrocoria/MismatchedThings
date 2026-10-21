package games.alejandrocoria.mismatchedboots.compatibility.aether;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AetherRecognitionShapeTest {
    @Test
    void nativeLocalSitesAreEditedOnceWithoutDependingOnUnrelatedMethods() throws Exception {
        for (String target : new String[]{AetherRecognitionShape.EQUIPMENT, AetherRecognitionShape.SENTRY}) {
            var node = nativeClass(target);
            var unrelated = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "foreignObservation", "()V", null, null);
            unrelated.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(unrelated);
            assertTrue(AetherRecognitionShape.apply(node), target);
            assertFalse(AetherRecognitionShape.apply(node), "Must not insert a second hook");
        }
    }

    @Test
    void compatibleExpressionResultChainsKeepTheFinalPeerResult() throws Exception {
        for (String target : new String[]{AetherRecognitionShape.EQUIPMENT, AetherRecognitionShape.SENTRY}) {
            var node = nativeClass(target);
            var method = queryMethod(node);
            boolean sets = target.equals(AetherRecognitionShape.EQUIPMENT);
            var query = java.util.Arrays.stream(method.instructions.toArray())
                    .filter(n -> n instanceof MethodInsnNode call && call.owner.equals(AetherRecognitionShape.STACK)
                            && call.name.equals(sets ? "get" : "is")).findFirst().orElseThrow();
            String descriptor = sets ? "(Ljava/lang/Object;)Ljava/lang/Object;" : "(Z)Z";
            method.instructions.insert(query, new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Peer", "second", descriptor, false));
            method.instructions.insert(query, new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Peer", "first", descriptor, false));
            if (sets) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.IRETURN) {
                        method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "fixture/Peer", "finalCount", "(I)I", false));
                    }
                    if (instruction instanceof VarInsnNode store && store.getOpcode() == Opcodes.ISTORE
                            && instruction.getPrevious().getOpcode() == Opcodes.ICONST_0) {
                        method.instructions.set(instruction.getPrevious(), new InsnNode(Opcodes.ICONST_1));
                    }
                }
            }
            assertTrue(AetherRecognitionShape.apply(node), target);
        }
    }

    @Test
    void changedSetSourceOrSinglePieceCounterCannotGrantRecognition() throws Exception {
        for (String fault : new String[]{"component", "increment", "equipment"}) {
            var node = nativeClass(AetherRecognitionShape.EQUIPMENT);
            var method = queryMethod(node);
            for (var instruction : method.instructions) {
                if (fault.equals("component") && instruction instanceof FieldInsnNode field && field.name.equals("ARMOR_SET")) { field.name = "OTHER_COMPONENT"; }
                if (fault.equals("increment") && instruction instanceof IincInsnNode increment) { increment.incr = 2; }
                if (fault.equals("equipment") && instruction instanceof MethodInsnNode call && call.name.equals("getEquipment")) { call.name = "foreignEquipment"; }
            }
            assertFalse(AetherRecognitionShape.apply(node), fault);
        }
    }

    @Test
    void changedSentryIdentitySlotOrEventLaneCannotGrantProtection() throws Exception {
        for (String fault : new String[]{"item", "slot", "actor"}) {
            var node = nativeClass(AetherRecognitionShape.SENTRY);
            for (var instruction : queryMethod(node).instructions) {
                if (fault.equals("item") && instruction instanceof FieldInsnNode field && field.name.equals("SENTRY_BOOTS")) { field.name = "NEPTUNE_BOOTS"; }
                if (fault.equals("slot") && instruction instanceof FieldInsnNode field && field.name.equals("FEET")) { field.name = "HEAD"; }
                if (fault.equals("actor") && instruction instanceof MethodInsnNode call && call.name.equals("getEntity")) { call.name = "foreignEntity"; }
            }
            assertFalse(AetherRecognitionShape.apply(node), fault);
        }
    }

    private static MethodNode queryMethod(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals(node.name.equals(AetherRecognitionShape.EQUIPMENT)
                ? "getArmorCount" : "playerFall")).findFirst().orElseThrow();
    }
    private static ClassNode nativeClass(String name) throws Exception {
        String path = System.getProperty("mismatchedboots.test.aetherGuardJar");
        assumeTrue(path != null, "Native Aether shape characterization is opt-in");
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
}
