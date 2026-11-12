package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustDireMovementShapeTest implements Opcodes {
    @Test
    void nativeCallbacksAllowUnrelatedPeers() throws Exception {
        var node = nativeOwner();
        assertTrue(JustDireMovementShape.entry(node));
        node.methods.forEach(method -> method.instructions.insert(new InsnNode(NOP)));
        node.methods.add(new MethodNode(ACC_PUBLIC | ACC_STATIC, "unrelated", "()V", null, null));
        assertTrue(JustDireMovementShape.entry(node));
    }

    @Test
    void missingMovementAndWrongTemporaryStackRefuseTheRoute() throws Exception {
        for (String name : List.of("walkSpeed", "runSpeed", "swimSpeed")) {
            var node = nativeOwner();
            var method = node.methods.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("moveRelative")) { call.name = "unknownMotion"; }
            }
            assertFalse(JustDireMovementShape.entry(node), name);
            node = nativeOwner();
            method = node.methods.stream().filter(value -> value.name.equals(name)).findFirst().orElseThrow();
            for (var instruction : method.instructions) {
                if (instruction instanceof VarInsnNode variable && variable.getOpcode() == ALOAD && variable.var == 2) {
                    method.instructions.set(variable, new InsnNode(ACONST_NULL));
                }
            }
            assertFalse(JustDireMovementShape.entry(node), name);
        }
    }

    private static ClassNode nativeOwner() throws Exception {
        try (var zip = new ZipFile(System.getProperty("mismatchedleggings.test.jdtJar"));
             var stream = zip.getInputStream(zip.getEntry(JustDireMovementShape.OWNER + ".class"))) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
