package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JustDireRefundShapeTest implements Opcodes {
    @Test void nativeConsumptionSliceIgnoresUnrelatedMethodsButCannotPublishPermission() throws Exception {
        var node = nativeClass(); assertTrue(JustDireRefundShape.entry(node)); assertFalse(JustDireRefundShape.publish(node));
        var unrelated = new MethodNode(ACC_PRIVATE | ACC_STATIC, "foreignUnrelated", "()V", null, null);
        unrelated.instructions.add(new InsnNode(RETURN)); node.methods.add(unrelated);
        assertTrue(JustDireRefundShape.entry(node));
    }

    @Test void consumptionRequiresCorrectCountDamageAndBrokenBranch() throws Exception {
        for (String fault : new String[]{"count", "extra", "damage", "branch", "descriptor"}) {
            var node = nativeClass(); var apply = apply(node); int changed = 0;
            if (fault.equals("descriptor")) { apply.desc = "(ILjava/util/function/Consumer;)V"; changed++; }
            else {
                for (var instruction : apply.instructions) {
                    if (!(instruction instanceof MethodInsnNode call)) { continue; }
                    if (fault.equals("count") && call.name.equals("shrink")) {
                        apply.instructions.set(call.getPrevious(), new InsnNode(ICONST_2)); changed++;
                    } else if (fault.equals("extra") && call.name.equals("shrink")) {
                        var zero = new InsnList(); zero.add(new VarInsnNode(ALOAD, 0)); zero.add(new InsnNode(ICONST_0));
                        zero.add(new MethodInsnNode(INVOKEVIRTUAL, JustDireRefundShape.OWNER, "shrink", "(I)V", false)); apply.instructions.insertBefore(call, zero); changed++;
                    } else if (fault.equals("damage") && call.name.equals("setDamageValue")) {
                        apply.instructions.set(call.getPrevious(), new InsnNode(ICONST_0)); changed++;
                    } else if (fault.equals("branch") && call.name.equals("isBroken")) {
                        apply.instructions.set(call, new MethodInsnNode(INVOKEVIRTUAL, JustDireRefundShape.OWNER, "isEmpty", "()Z", false)); changed++;
                    }
                }
            }
            assertEquals(1, changed); assertFalse(JustDireRefundShape.entry(node), fault);
        }
    }

    @Test void realExportPublishesOnlyAfterTheLocalProof() throws Exception {
        var node = exported("Live"); assertTrue(JustDireRefundShape.publish(node));
        assertEquals(ICONST_1, getter(node).instructions.getFirst().getOpcode());
    }

    @Test void actualForeignWrappersAreAcceptedButChangedForwardingIsNot() throws Exception {
        for (String variant : new String[]{"Inner", "Outer"}) {
            var node = exported(variant); assertTrue(JustDireRefundShape.publish(node), variant);
            node = exported(variant);
            var forwarder = node.methods.stream().filter(m -> m.name.contains("refundTest$") && !m.name.contains("bridge")).findFirst().orElseThrow();
            int changed = 0;
            for (var instruction : forwarder.instructions) {
                if (instruction instanceof VarInsnNode load && load.getOpcode() == ILOAD && load.var == 2) {
                    forwarder.instructions.set(load, new InsnNode(ICONST_2)); changed++;
                }
            }
            assertEquals(1, changed); assertFalse(JustDireRefundShape.publish(node), variant + " altered forwarding");
        }
    }

    @Test void finalRouterOrReadinessTamperingNeutralizesOnlyTheOwnedHandler() throws Exception {
        for (String fault : new String[]{"router", "publication"}) {
            var node = exported("Live"); int changed = 0;
            if (fault.equals("publication")) { getter(node).instructions.set(getter(node).instructions.getFirst(), new InsnNode(ICONST_1)); changed++; }
            else {
                for (var method : node.methods) {
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode call && call.name.equals(JustDireRefundShape.READY)) {
                            method.instructions.set(call, new InsnNode(ICONST_1)); changed++;
                        }
                    }
                }
            }
            assertEquals(1, changed); assertFalse(JustDireRefundShape.publish(node));
            var handler = node.methods.stream().filter(m -> m.desc.equals(JustDireRefundShape.HANDLER)).findFirst().orElseThrow();
            assertEquals(ICONST_0, handler.instructions.getFirst().getOpcode());
            assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
        }
    }

    @Test void originalOperationCannotSubstituteTheForwardedShrinkCount() throws Exception {
        var node = exported("Live");
        var bridge = node.methods.stream().filter(m -> m.name.startsWith("mixinextras$bridge$shrink")).findFirst().orElseThrow();
        int changed = 0;
        for (var instruction : bridge.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("intValue")) {
                var add = new InsnList(); add.add(new InsnNode(ICONST_1)); add.add(new InsnNode(IADD));
                bridge.instructions.insert(call, add); changed++;
            }
        }
        assertEquals(1, changed); assertFalse(JustDireRefundShape.publish(node));
        assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
    }

    private static ClassNode nativeClass() throws Exception {
        var node = new ClassNode();
        try (var stream = JustDireRefundShapeTest.class.getClassLoader().getResourceAsStream(JustDireRefundShape.OWNER + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static ClassNode exported(String variant) throws Exception {
        String file = System.getProperty("mismatchedboots.test.justDireRefundTransformedClass", "");
        assumeTrue(!file.isEmpty(), "Supply actual production exports for final-shape acceptance");
        var path = Path.of(file).resolveSibling(variant + "-ItemStack.class");
        assertTrue(Files.isRegularFile(path), "Missing actual " + variant + " export");
        var node = new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node, 0);
        for (var instruction : getter(node).instructions) { if (instruction.getOpcode() >= 0) { getter(node).instructions.set(instruction, new InsnNode(ICONST_0)); break; } }
        return node;
    }
    private static MethodNode apply(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("applyDamage") && m.desc.equals(JustDireRefundShape.APPLY)).findFirst().orElseThrow(); }
    private static MethodNode getter(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals(JustDireRefundShape.READY)).findFirst().orElseThrow(); }
}
