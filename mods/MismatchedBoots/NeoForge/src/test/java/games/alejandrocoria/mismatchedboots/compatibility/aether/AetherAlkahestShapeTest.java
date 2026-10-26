package games.alejandrocoria.mismatchedboots.compatibility.aether;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AetherAlkahestShapeTest implements Opcodes {
    @Test
    void nativeDomainsAreAtomicAndDoNotDependOnUnrelatedPresenceReads() throws Exception {
        for (String target : new String[]{AetherAlkahestShape.FLUID, AetherAlkahestShape.EVENTS}) {
            var node = nativeClass(target);
            var method = targetMethod(node);
            var unrelated = new InsnList();
            unrelated.add(new FieldInsnNode(GETSTATIC, AetherAlkahestShape.STACK, "EMPTY", "L" + AetherAlkahestShape.STACK + ";"));
            unrelated.add(tier());
            unrelated.add(has());
            unrelated.add(new InsnNode(POP));
            method.instructions.insert(unrelated);
            assertTrue(AetherAlkahestShape.apply(node));
            assertEquals(target.equals(AetherAlkahestShape.FLUID) ? 3 : 1, hooks(method));
            assertFalse(AetherAlkahestShape.apply(node), "Cannot insert twice");
        }
    }

    @Test
    void provenLocalReceiverSupplierAndBooleanChainsRemainCompatible() throws Exception {
        for (String target : new String[]{AetherAlkahestShape.FLUID, AetherAlkahestShape.EVENTS}) {
            var node = nativeClass(target);
            var method = targetMethod(node);
            var query = firstQuery(method);
            for (String name : new String[]{"first", "second"}) {
                identity(node, name + "Stack", "L" + AetherAlkahestShape.STACK + ";");
                identity(node, name + "Supplier", "Ljava/util/function/Supplier;");
                method.instructions.insertBefore(query.getPrevious(), new MethodInsnNode(INVOKESTATIC,
                                                                                         node.name,
                                                                                         name + "Stack",
                                                                                         "(L" + AetherAlkahestShape.STACK + ";)L" + AetherAlkahestShape.STACK + ";",
                                                                                         false));
            }

            var supply = query.getPrevious();
            for (String name : new String[]{"first", "second"}) {
                method.instructions.insert(supply, new MethodInsnNode(INVOKESTATIC, node.name, name + "Supplier",
                                                                      "(Ljava/util/function/Supplier;)Ljava/util/function/Supplier;", false));
                method.instructions.insert(query, new MethodInsnNode(INVOKESTATIC, "fixture/Peer", name, "(Z)Z", false));
            }
            assertTrue(AetherAlkahestShape.apply(node), target);
        }
    }

    @Test
    void opaqueReceiverOrSupplierRefusesOnlyItsDomainWithoutPartialEdits() throws Exception {
        for (String target : new String[]{AetherAlkahestShape.FLUID, AetherAlkahestShape.EVENTS}) {
            for (String fault : new String[]{"receiver", "supplier", "component"}) {
                var node = nativeClass(target);
                var method = targetMethod(node);
                var query = firstQuery(method);
                if (fault.equals("component")) {
                    ((FieldInsnNode) query.getPrevious()).name = "ARMOR_SET";
                    assertFalse(AetherAlkahestShape.apply(node));
                    assertEquals(0, hooks(method));
                    continue;
                }

                boolean receiver = fault.equals("receiver");
                var opaque = new MethodInsnNode(INVOKESTATIC, "fixture/Peer", "opaque",
                                                receiver ? "(L" + AetherAlkahestShape.STACK + ";)L" + AetherAlkahestShape.STACK + ";"
                                                         : "(Ljava/util/function/Supplier;)Ljava/util/function/Supplier;", false);
                if (receiver) {
                    method.instructions.insertBefore(query.getPrevious(), opaque);
                } else {
                    method.instructions.insertBefore(query, opaque);
                }
                assertFalse(AetherAlkahestShape.apply(node), target + "/" + fault);
                assertEquals(0, hooks(method));
            }
        }
    }

    @Test
    void missingOrAmbiguousFluidSeamDoesNotLeaveTwoWorkingHooks() throws Exception {
        for (boolean duplicate : new boolean[]{false, true}) {
            var node = nativeClass(AetherAlkahestShape.FLUID);
            var method = targetMethod(node);
            var query = firstQuery(method);
            if (duplicate) {
                var branch = (JumpInsnNode) query.getNext();
                var extra = new InsnList();
                extra.add(new VarInsnNode(ALOAD, ((VarInsnNode) query.getPrevious().getPrevious()).var));
                extra.add(tier());
                extra.add(has());
                extra.add(new JumpInsnNode(IFNE, branch.label));
                method.instructions.insert(branch, extra);
            } else {
                query.name = "changedPresenceQuery";
            }
            assertFalse(AetherAlkahestShape.apply(node));
            assertEquals(0, hooks(method));
        }
    }

    private static void identity(ClassNode node, String name, String type) {
        var method = new MethodNode(ACC_PRIVATE | ACC_STATIC, name, "(" + type + ")" + type, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new InsnNode(ARETURN));
        node.methods.add(method);
    }

    private static FieldInsnNode tier() {
        return new FieldInsnNode(GETSTATIC,
                                 AetherAlkahestShape.COMPONENTS,
                                 "REINFORCEMENT_TIER",
                                 "Lnet/neoforged/neoforge/registries/DeferredHolder;");
    }

    private static MethodInsnNode has() {
        return new MethodInsnNode(INVOKEVIRTUAL, AetherAlkahestShape.STACK, "has", AetherAlkahestShape.HAS, false);
    }

    private static long hooks(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                     .filter(n -> n instanceof MethodInsnNode c && c.owner.equals(AetherAlkahestShape.ADAPTER))
                     .count();
    }

    private static MethodInsnNode firstQuery(MethodNode method) {
        return (MethodInsnNode) Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode c
                && c.owner.equals(AetherAlkahestShape.STACK) && c.name.equals("has")).findFirst().orElseThrow();
    }

    private static MethodNode targetMethod(ClassNode node) {
        return node.methods.stream()
                           .filter(m -> m.name.equals(node.name.equals(AetherAlkahestShape.FLUID) ? "entityInside" : "onArmorDamaged"))
                           .findFirst()
                           .orElseThrow();
    }

    private static ClassNode nativeClass(String name) throws Exception {
        String path = System.getProperty("mismatchedboots.test.aetherGuardJar");
        assumeTrue(path != null, "Native Aether guard characterization is opt-in");
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
