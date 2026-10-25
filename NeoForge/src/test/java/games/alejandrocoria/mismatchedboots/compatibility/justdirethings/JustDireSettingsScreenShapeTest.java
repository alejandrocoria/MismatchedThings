package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Native seam admission and final owned callback survival, not whole-method fingerprints. */
class JustDireSettingsScreenShapeTest implements Opcodes {
    @Test
    void nativeScreenRequiresExactCallbackSignaturesAndInstanceState() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireGuardJar");
        assumeTrue(path != null, "Native artifact characterization is opt-in");
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(JustDireSettingsScreenShape.SCREEN + ".class"))) {
            byte[] bytes = stream.readAllBytes();
            assertTrue(JustDireSettingsScreenShape.entry(read(bytes)));
            var changed = read(bytes);
            method(changed, "setSetting").desc = "(Ljava/lang/String;J)V";
            assertFalse(JustDireSettingsScreenShape.entry(changed));
            changed = read(bytes);
            changed.fields.stream().filter(f -> f.name.equals("toolSlot")).findFirst().orElseThrow().access |= ACC_STATIC;
            assertFalse(JustDireSettingsScreenShape.entry(changed));
            changed = read(bytes);
            method(changed, "refreshButtons").instructions.insert(new InsnNode(NOP));
            assertTrue(JustDireSettingsScreenShape.entry(changed), "Unrelated work must not deny the bridge");
        }
    }

    @Test
    void realFinalCallbacksPublishButMissingOrForeignCallbackFailsClosed() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireSettingsTransformedClass");
        assumeTrue(path != null, "Final screen export is opt-in");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        var node = reset(bytes);
        assertFalse(node.methods.stream().anyMatch(m -> m.name.contains("mismatchedboots$jdtToggleKey")),
                    "Final native screen must not retain the retired mixed global-key injection");
        assertTrue(JustDireSettingsScreenShape.publish(node));
        node = reset(bytes);
        method(node, "setSetting").instructions.clear();
        method(node, "setSetting").instructions.add(new InsnNode(RETURN));
        assertFalse(JustDireSettingsScreenShape.publish(node), "Lost numeric callback must disable all mixed selection");
        assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
        node = reset(bytes);
        node.methods.stream().filter(m -> m.name.contains("mismatchedboots$jdtBinding"))
                    .forEach(m -> m.visibleAnnotations.clear());
        assertFalse(JustDireSettingsScreenShape.publish(node), "A foreign handler cannot satisfy owned interception");
        node = reset(bytes);
        method(node, "mouseClicked").instructions.insert(new InsnNode(NOP));
        assertTrue(JustDireSettingsScreenShape.publish(node), "Compatible extra work retains all required hooks");
        node = reset(bytes);
        node.methods.stream().filter(m -> m.name.contains("mismatchedboots$jdtMouseCaptureGuard"))
                    .forEach(m -> m.visibleAnnotations.clear());
        assertFalse(JustDireSettingsScreenShape.publish(node), "Lost mouse capture guard must not admit invalid additional buttons");
        node = reset(bytes);
        node.methods.stream().filter(m -> m.name.contains("mismatchedboots$jdtLocation"))
                    .forEach(m -> m.visibleAnnotations.clear());
        assertFalse(JustDireSettingsScreenShape.publish(node), "Lost native capture preference repair must disable mixed controls");
        node = reset(bytes);
        getter(node).instructions.set(getter(node).instructions.getFirst(), new InsnNode(ICONST_1));
        assertFalse(JustDireSettingsScreenShape.publish(node), "External readiness override fails closed");
        assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
    }

    private static ClassNode reset(byte[] bytes) {
        var node = read(bytes);
        var ready = getter(node);
        for (var instruction : ready.instructions) {
            if (instruction.getOpcode() == ICONST_1) {
                ready.instructions.set(instruction, new InsnNode(ICONST_0));
                break;
            }
        }
        return node;
    }

    private static ClassNode read(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodNode getter(ClassNode node) {
        return method(node, "mismatchedboots$jdtSettingsReady");
    }
}
