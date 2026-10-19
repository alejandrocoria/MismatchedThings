package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RootsTickShapeTest implements Opcodes {
    @Test void supportedStackConsumersTolerateUnrelatedInstrumentationAndRepairBalance() {
        ClassNode tick = tick();
        assertTrue(RootsTickShape.tick(tick));
        tick.methods.getFirst().instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "tick", "()V", false));
        tick.methods.getFirst().instructions.insert(new LabelNode());
        tick.methods.getFirst().instructions.insert(new InsnNode(NOP));
        assertTrue(RootsTickShape.tick(tick));
        ClassNode repair = repair();
        for (var node : repair.methods.getFirst().instructions) {
            if (node instanceof IntInsnNode number) { number.operand = 160; }
        }
        repair.methods.getFirst().instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "repair", "()V", false));
        assertTrue(RootsTickShape.repair(repair));
    }

    @Test void rootEscapesWrongSlotAndArgumentReassignmentCannotGrantTickPermission() {
        for (String fault : new String[]{"escape", "slot", "overwrite", "duplicate", "receiver", "descriptor"}) {
            ClassNode owner = tick();
            MethodNode method = owner.methods.getFirst();
            switch (fault) {
                case "escape" -> {
                    method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Consumer", "accept",
                            "(L" + RootsTickShape.STACK + ";)V", false));
                    method.instructions.insert(new VarInsnNode(ALOAD, 1));
                }
                case "slot" -> {
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof VarInsnNode variable && variable.var == 4) {
                            method.instructions.set(instruction, new FieldInsnNode(GETSTATIC, RootsTickShape.SLOT,
                                    "HEAD", "L" + RootsTickShape.SLOT + ";"));
                        }
                    }
                }
                case "overwrite" -> {
                    method.instructions.insert(new VarInsnNode(ASTORE, 1));
                    method.instructions.insert(new InsnNode(ACONST_NULL));
                }
                case "duplicate" -> method.instructions.add(tick().methods.getFirst().instructions);
                case "receiver" -> method.instructions.set(method.instructions.get(6),
                        new FieldInsnNode(GETSTATIC, "fixture/Context", "otherLevel", "L" + RootsTickShape.LEVEL + ";"));
                case "descriptor" -> method.desc = "()V";
                default -> throw new AssertionError(fault);
            }
            assertFalse(RootsTickShape.tick(owner), fault);
        }
    }

    @Test void repairRequiresRootDamageDecrementWithoutAliasingOrUnknownConsumers() {
        for (String fault : new String[]{"escape", "alias", "addition", "healthy", "destination", "static"}) {
            ClassNode owner = repair();
            MethodNode method = owner.methods.getFirst();
            switch (fault) {
                case "escape" -> {
                    method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Consumer", "accept",
                            "(L" + RootsTickShape.STACK + ";)V", false));
                    method.instructions.insert(new VarInsnNode(ALOAD, 1));
                }
                case "alias" -> {
                    method.instructions.insert(new VarInsnNode(ASTORE, 2));
                    method.instructions.insert(new VarInsnNode(ALOAD, 1));
                }
                case "addition" -> {
                    for (var node : method.instructions.toArray()) {
                        if (node.getOpcode() == ISUB) { method.instructions.set(node, new InsnNode(IADD)); }
                    }
                }
                case "healthy" -> ((JumpInsnNode) method.instructions.get(2)).setOpcode(IFNE);
                case "destination" -> method.instructions.set(method.instructions.get(7),
                        new FieldInsnNode(GETSTATIC, "fixture/Context", "otherStack", "L" + RootsTickShape.STACK + ";"));
                case "static" -> method.access = ACC_PUBLIC;
                default -> throw new AssertionError(fault);
            }
            assertFalse(RootsTickShape.repair(owner), fault);
        }
    }

    @Test void proofPublicationRequiresAnUntouchedOwnedGetterAndClearsStalePermission() {
        ClassNode owner = tick();
        MethodNode getter = new MethodNode(ACC_PUBLIC, RootsTickShape.ENTRY_PROOF, "()Z", null, null);
        getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN));
        owner.methods.add(getter);
        RootsTickShape.publish(owner, RootsTickShape.ENTRY_PROOF, false, true);
        assertEquals(ICONST_1, getter.instructions.getFirst().getOpcode());
        RootsTickShape.publish(owner, RootsTickShape.ENTRY_PROOF, false, false);
        assertEquals(ICONST_0, getter.instructions.getFirst().getOpcode());
        getter.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "proof", "()V", false));
        RootsTickShape.publish(owner, RootsTickShape.ENTRY_PROOF, false, true);
        assertEquals(ICONST_0, getter.instructions.getFirst().getOpcode());

        ClassNode helper = repair();
        MethodNode proof = new MethodNode(ACC_PRIVATE | ACC_STATIC, RootsTickShape.REPAIR_PROOF, "()Z", null, null);
        proof.instructions.add(new InsnNode(ICONST_0)); proof.instructions.add(new InsnNode(IRETURN));
        helper.methods.add(proof);
        RootsTickShape.publish(helper, RootsTickShape.REPAIR_PROOF, true, RootsTickShape.repair(helper));
        assertEquals(ACC_PUBLIC | ACC_STATIC, proof.access);
        assertEquals(ICONST_1, proof.instructions.getFirst().getOpcode());
    }

    @Test void installedNativeCallbacksAndHelperHaveTheAuditedStackBoundary() throws Exception {
        String path = System.getProperty("mismatchedboots.test.rootsGuardJar");
        assumeTrue(path != null, "Real-artifact guard characterization is opt-in");
        try (JarFile jar = new JarFile(path)) {
            assertTrue(RootsTickShape.tick(read(jar, RootsTickShape.SYLVAN)));
            assertTrue(RootsTickShape.tick(read(jar, RootsTickShape.WILDWOOD)));
            assertTrue(RootsTickShape.repair(read(jar, RootsTickShape.UTIL)));
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) { new ClassReader(stream).accept(node, 0); }
        return node;
    }

    private static ClassNode tick() {
        ClassNode node = new ClassNode(); node.name = RootsTickShape.SYLVAN; node.superName = RootsTickShape.ITEM;
        MethodNode method = new MethodNode(ACC_PUBLIC, "inventoryTick", RootsTickShape.TICK, null, null);
        node.methods.add(method);
        LabelNode exit = new LabelNode();
        method.instructions.add(new VarInsnNode(ALOAD, 4)); method.instructions.add(new JumpInsnNode(IFNULL, exit));
        method.instructions.add(new VarInsnNode(ALOAD, 4));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, RootsTickShape.SLOT, "getIndex", "()I", false));
        method.instructions.add(new InsnNode(ICONST_4)); method.instructions.add(new JumpInsnNode(IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(ALOAD, 2));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, RootsTickShape.LEVEL, "getRandom", "()Lnet/minecraft/util/RandomSource;", false));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, RootsTickShape.UTIL, "randomlyRepair", RootsTickShape.REPAIR, false));
        method.instructions.add(exit); method.instructions.add(new InsnNode(RETURN));
        return node;
    }

    private static ClassNode repair() {
        ClassNode node = new ClassNode(); node.name = RootsTickShape.UTIL;
        MethodNode method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "randomlyRepair", RootsTickShape.REPAIR, null, null);
        node.methods.add(method);
        LabelNode exit = new LabelNode();
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, RootsTickShape.STACK, "isDamaged", "()Z", false));
        method.instructions.add(new JumpInsnNode(IFEQ, exit)); method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new IntInsnNode(BIPUSH, 80));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "net/minecraft/util/RandomSource", "nextInt", "(I)I", true));
        method.instructions.add(new JumpInsnNode(IFNE, exit)); method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, RootsTickShape.STACK, "getDamageValue", "()I", false));
        method.instructions.add(new InsnNode(ICONST_1)); method.instructions.add(new InsnNode(ISUB));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, RootsTickShape.STACK, "setDamageValue", "(I)V", false));
        method.instructions.add(exit); method.instructions.add(new InsnNode(RETURN));
        return node;
    }
}
