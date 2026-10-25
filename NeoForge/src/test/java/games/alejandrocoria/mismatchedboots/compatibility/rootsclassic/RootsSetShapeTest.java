package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RootsSetShapeTest implements Opcodes {
    @Test
    void nativePredicatesAreEditedOnceAndUnrelatedSlotWrappersRemainUntouched() throws Exception {
        for (String target : targets()) {
            ClassNode node = nativeClass(target);
            var method = consumer(node);
            method.instructions.insert(new InsnNode(NOP));
            method.instructions.insert(new LabelNode());
            method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "note", "()V", false));
            // A separate adapter can extend HEAD without synchronizing its implementation with FEET.
            if (!target.equals(RootsSetShape.EVENTS)) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == INSTANCEOF) {
                        method.instructions.insert(type, new MethodInsnNode(INVOKESTATIC, "fixture/Head", "result", "(Z)Z", false));
                        break;
                    }
                }
            }
            assertTrue(RootsSetShape.apply(node), target);
            assertEquals(1, java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                    && call.owner.endsWith("/RootsSetCompatibility")).count());
            assertFalse(RootsSetShape.apply(node), "must not insert twice");
        }
    }

    @Test
    void missingAmbiguousOrRepurposedFeetPredicatesFailWithoutEditing() throws Exception {
        for (String target : targets()) {
            for (String fault : new String[]{"slot", "receiver", "ambiguous", "flow", "item", "player", "potency"}) {
                if (fault.equals("potency") && target.equals(RootsSetShape.EVENTS)) {
                    continue;
                }
                ClassNode node = nativeClass(target);
                var method = consumer(node);
                FieldInsnNode feet = java.util.Arrays.stream(method.instructions.toArray())
                                                     .filter(n -> n instanceof FieldInsnNode f && f.name.equals("FEET"))
                                                     .map(n -> (FieldInsnNode) n).findFirst().orElseThrow();
                switch (fault) {
                    case "slot" -> feet.name = "HEAD";
                    case "receiver" -> ((MethodInsnNode) next(feet)).owner = "fixture/OtherPlayer";
                    case "ambiguous" -> method.instructions.insert(new FieldInsnNode(GETSTATIC, RootsSetShape.SLOT, "FEET", feet.desc));
                    case "potency" -> {
                        for (var instruction : method.instructions) {
                            if (instruction instanceof IincInsnNode increment) {
                                increment.var++;
                                break;
                            }
                        }
                    }
                    case "player" -> {
                        method.instructions.insert(new VarInsnNode(ASTORE, 3));
                        method.instructions.insert(new InsnNode(ACONST_NULL));
                        if (target.equals(RootsSetShape.EVENTS)) {
                            method.instructions.insert(new VarInsnNode(ASTORE, 1));
                            method.instructions.insert(new InsnNode(ACONST_NULL));
                        }
                    }
                    case "item", "flow" -> {
                        boolean afterFeet = false;
                        boolean afterBootItem = !target.equals(RootsSetShape.EVENTS);
                        for (var instruction : method.instructions.toArray()) {
                            if (instruction == feet) {
                                afterFeet = true;
                            }
                            if (!afterFeet) {
                                continue;
                            }
                            if (instruction instanceof FieldInsnNode field && field.name.equals("WILDWOOD_BOOTS")) {
                                afterBootItem = true;
                            }
                            if (fault.equals("item") && instruction instanceof TypeInsnNode type && type.getOpcode() == INSTANCEOF) {
                                type.desc = RootsTickShape.WILDWOOD;
                                break;
                            }
                            if (fault.equals("item") && instruction instanceof FieldInsnNode field && field.name.equals("WILDWOOD_BOOTS")) {
                                field.name = "WILDWOOD_MASK";
                                break;
                            }
                            if (fault.equals("flow") && afterBootItem && instruction instanceof JumpInsnNode jump) {
                                jump.setOpcode(jump.getOpcode() == IFEQ ? IFNE : IF_ACMPEQ);
                                break;
                            }
                        }
                    }
                    default -> throw new AssertionError(fault);
                }
                int size = method.instructions.size();
                assertFalse(RootsSetShape.apply(node), target + ": " + fault);
                assertEquals(size, method.instructions.size(), "rejection must leave native instructions unchanged");
            }
        }
    }

    private static String[] targets() {
        return new String[]{RootsSetShape.STAFF, RootsSetShape.CRYSTAL, RootsSetShape.EVENTS};
    }

    private static org.objectweb.asm.tree.MethodNode consumer(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals(node.name.equals(RootsSetShape.EVENTS)
                                                               ? "wildwoodArmorRegenFullset" : "releaseUsing")).findFirst().orElseThrow();
    }

    private static org.objectweb.asm.tree.AbstractInsnNode next(org.objectweb.asm.tree.AbstractInsnNode node) {
        do {
            node = node.getNext();
        } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static ClassNode nativeClass(String name) throws Exception {
        String path = System.getProperty("mismatchedboots.test.rootsGuardJar");
        assumeTrue(path != null, "Opt-in pinned Roots native guard test");
        try (JarFile jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
