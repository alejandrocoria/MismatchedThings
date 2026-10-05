package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.ENTITY;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.armor;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.classCheck;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.contribution;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.damage;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.fingerprint;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.handler;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.read;
import static games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireGuardFixture.start;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IceAndFireDamageShapeTest {
    @Test
    void installedArtifactIsCharacterizedButBalanceEditsDoNotDisableBootRecognition() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Real-artifact characterization is opt-in");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            ClassNode handler = read(jar, IceAndFireDamageShape.HANDLER);
            MethodNode method = handler.methods.stream()
                    .filter(node -> IceAndFireDamageShape.METHOD.equals(node.name)).findFirst().orElseThrow();
            assertEquals("05b8d47b296aa4ad4de6e3aa9c1a477fc14789d5265617a5c518b1553d1bbdc9", fingerprint(method));
            assertTrue(IceAndFireDamageShape.isCompatible(handler, read(jar, IceAndFireDamageShape.SCALE),
                    read(jar, IceAndFireDamageShape.STEEL), read(jar, IceAndFireDamageShape.TROLL)));
            assertTrue(IceAndFireDamageFeetInjectionPoint.matchesLocalContract(method.desc, method.instructions));
            String before = fingerprint(method);
            for (var node : method.instructions) {
                if (node instanceof LdcInsnNode constant && Float.valueOf(0.1F).equals(constant.cst)) {
                    constant.cst = 0.05F;
                }
            }
            assertNotEquals(before, fingerprint(method));
            assertTrue(IceAndFireDamageFeetInjectionPoint.matchesLocalContract(method.desc, method.instructions));
        }
    }

    @Test
    void selectorExcludesOtherSlotsAndRejectsMissingOrAmbiguousFeet() {
        MethodNode method = damage();
        var selected = IceAndFireDamageShape.feetChecks(method.instructions);
        assertEquals(3, selected.size());
        assertEquals(IceAndFireDamageShape.TROLL, ((TypeInsnNode) selected.getFirst()).desc);
        ((FieldInsnNode) selected.getLast().getPrevious().getPrevious().getPrevious()).name = "HEAD";
        assertFalse(accepts(method));

        method = damage();
        // A second legitimate-looking feet contribution is ambiguous, even with an unchanged API.
        var extra = start("unused");
        classCheck(extra, "FEET", IceAndFireDamageShape.TROLL);
        LabelNode skip = new LabelNode();
        extra.instructions.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        contribution(extra);
        extra.instructions.add(skip);
        extra.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(extra.instructions);
        assertFalse(accepts(method));
    }

    @Test
    void unrelatedCallsMetadataAndRelocatedEntityLocalAreAccepted() {
        MethodNode method = damage();
        LabelNode debug = new LabelNode();
        method.instructions.insert(debug);
        method.instructions.insert(debug, new LineNumberNode(999, debug));
        method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(Opcodes.NOP));
        method.instructions.insertBefore(method.instructions.getLast(),
                new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Logger", "tick", "()V", false));
        for (var node : method.instructions) {
            if (node instanceof VarInsnNode variable && variable.var == 1) {
                variable.var = 9;
            }
        }
        assertTrue(accepts(method));
        // Replacing a different slot's predicate with a sibling wrapper leaves FEET untouched.
        for (var node : method.instructions) {
            if (node instanceof TypeInsnNode check && check.getOpcode() == Opcodes.INSTANCEOF) {
                method.instructions.set(check,
                        new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Helmet", "recognize",
                                "(Ljava/lang/Object;)Z", false));
                break;
            }
        }
        assertTrue(accepts(method));
    }

    @Test
    void localBooleanConsumerEntityProvenanceAndSignatureMustStayCompatible() {
        MethodNode method = damage();
        var troll = IceAndFireDamageShape.feetChecks(method.instructions).getFirst();
        ((JumpInsnNode) troll.getNext()).setOpcode(Opcodes.IFNE);
        assertFalse(accepts(method));
        method = damage();
        troll = IceAndFireDamageShape.feetChecks(method.instructions).getFirst();
        method.instructions.set(troll.getNext().getNext().getNext().getNext(), new InsnNode(Opcodes.FADD));
        assertFalse(accepts(method));
        method = damage();
        var feetScale = IceAndFireDamageShape.feetChecks(method.instructions).get(1);
        var headScale = method.instructions.iterator();
        while (headScale.hasNext()) {
            if (headScale.next() instanceof TypeInsnNode check && IceAndFireDamageShape.SCALE.equals(check.desc)) {
                // A valid-looking subtraction from a different slot is not the selected FEET Scale/Steel OR.
                ((JumpInsnNode) feetScale.getNext()).label = ((JumpInsnNode) check.getNext()).label;
                break;
            }
        }
        assertFalse(accepts(method));
        method = damage();
        ((MethodInsnNode) method.instructions.get(1)).name = "unrelatedEntity";
        assertFalse(accepts(method));
        method = damage();
        InsnList reassignment = new InsnList();
        reassignment.add(new FieldInsnNode(Opcodes.GETSTATIC, "fixture/OtherEntity", "subject", "L" + ENTITY + ";"));
        reassignment.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.insertBefore(IceAndFireDamageShape.feetChecks(method.instructions).getFirst().getPrevious()
                .getPrevious().getPrevious().getPrevious(), reassignment);
        assertFalse(accepts(method));
        assertFalse(IceAndFireDamageFeetInjectionPoint.matchesLocalContract("(Ljava/lang/Object;)V", damage().instructions));
    }

    @Test
    void missingOrIncompatibleOptionalApiIsRejected() {
        MethodNode method = damage();
        ClassNode handler = handler(method);
        ClassNode scale = armor(IceAndFireDamageShape.SCALE);
        ClassNode steel = armor(IceAndFireDamageShape.STEEL);
        ClassNode troll = armor(IceAndFireDamageShape.TROLL);
        assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll));
        assertFalse(IceAndFireDamageShape.isCompatible(null, scale, steel, troll));
        assertFalse(IceAndFireDamageShape.isCompatible(handler, null, steel, troll));
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, null));
        troll.superName = "different/TrollBase";
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll));
        troll.superName = "net/minecraft/world/item/Item";
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll));
    }

    private static boolean accepts(MethodNode method) {
        return IceAndFireDamageFeetInjectionPoint.matchesLocalContract(method.desc, method.instructions);
    }
}
