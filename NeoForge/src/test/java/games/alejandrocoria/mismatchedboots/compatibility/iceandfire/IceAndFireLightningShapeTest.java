package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.fingerprint;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.handler;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.lightning;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.read;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IceAndFireLightningShapeTest {
    @Test
    void realArtifactAndOtherSlotExtensionsPreserveTheLocalFeetContract() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Real-artifact characterization is opt-in");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            ClassNode handler = read(jar, IceAndFireDamageShape.HANDLER);
            MethodNode method = handler.methods.stream()
                                               .filter(node -> IceAndFireDamageShape.LIGHTNING_METHOD.equals(node.name)).findFirst().orElseThrow();
            assertEquals("27d4490cc0ac9741b62dcbc4d2e39422cf12dbb8964c82decdf5cf41666f6169", fingerprint(method));
            assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, read(jar, IceAndFireDamageShape.ITEMS)));
            String before = fingerprint(method);
            for (var node : method.instructions) {
                if (node instanceof FieldInsnNode field && "DRAGONSTEEL_LIGHTNING_HELMET".equals(field.name)) {
                    field.name = "DRAGONSTEEL_FIRE_HELMET";
                }
            }
            assertNotEquals(before, fingerprint(method));
            assertTrue(accepts(method)); // The helmet predicate is not our contract.
        }
    }

    @Test
    void rejectsChangedFeetItemSlotConsumerAndAmbiguity() {
        MethodNode method = lightning();
        assertEquals(1, IceAndFireDamageShape.lightningFeetChecks(method.instructions).size());
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field && "DRAGONSTEEL_LIGHTNING_BOOTS".equals(field.name)) {
                field.name = "DRAGONSTEEL_FIRE_BOOTS";
            }
        }
        assertFalse(accepts(method));
        method = lightning();
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field && "FEET".equals(field.name)) {
                field.name = "HEAD";
            }
        }
        assertFalse(accepts(method));
        method = lightning();
        var predicate = IceAndFireDamageShape.lightningFeetChecks(method.instructions).getFirst();
        ((JumpInsnNode) predicate.getNext()).setOpcode(Opcodes.IFNE);
        assertFalse(accepts(method));
        method = lightning();
        predicate = IceAndFireDamageShape.lightningFeetChecks(method.instructions).getFirst();
        method.instructions.set(predicate.getNext().getNext().getNext(), new InsnNode(Opcodes.FCONST_1));
        assertFalse(accepts(method));
        method = lightning();
        method.instructions.add(lightning().instructions);
        assertFalse(accepts(method));
    }

    @Test
    void unrelatedInstructionsAndEntityLocalRelocationAreAcceptedButForeignEntityIsNot() {
        MethodNode method = lightning();
        method.instructions.insertBefore(method.instructions.getLast(),
                                         new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Logger", "tick", "()V", false));
        method.instructions.insert(new InsnNode(Opcodes.NOP));
        for (var node : method.instructions) {
            if (node instanceof VarInsnNode variable && variable.var == 1) {
                variable.var = 9;
            }
        }
        assertTrue(accepts(method));
        ((MethodInsnNode) method.instructions.get(2)).name = "unrelatedEntity";
        assertFalse(accepts(method));
    }

    @Test
    void independentGuardRejectsMissingApiAndHandlerSignature() {
        MethodNode method = lightning();
        ClassNode handler = handler(method); // No damage handler needed.
        ClassNode items = new ClassNode();
        items.name = IceAndFireDamageShape.ITEMS;
        items.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "DRAGONSTEEL_LIGHTNING_BOOTS",
                                       "Lnet/neoforged/neoforge/registries/DeferredItem;", null, null));
        assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, items));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(null, items));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, null));
        items.fields.getFirst().desc = "Ljava/lang/Object;";
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items));
        items.fields.getFirst().desc = "Lnet/neoforged/neoforge/registries/DeferredItem;";
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items));
    }

    private static boolean accepts(MethodNode method) {
        return IceAndFireLightningFeetInjectionPoint.matchesLocalContract(method.desc, method.instructions);
    }
}
