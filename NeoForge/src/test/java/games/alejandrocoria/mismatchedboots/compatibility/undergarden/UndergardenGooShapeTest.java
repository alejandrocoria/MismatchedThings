package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

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

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UndergardenGooShapeTest implements Opcodes {
    @Test
    void nativeArtifactIsExtendedOnceWithoutReplayingInventoryLookups() throws Exception {
        var owner = nativeClass();
        long getters = calls(owner, "getInventory");
        assertTrue(UndergardenGooShape.apply(owner));
        assertEquals(getters, calls(owner, "getInventory"));
        assertEquals(1, calls(owner, "goo"));
        assertFalse(UndergardenGooShape.apply(owner));
        assertEquals(1, calls(owner, "goo"));
    }

    @Test
    void localAliasesResultChainsAndUnrelatedBodyChangesRemainCompatible() throws Exception {
        var owner = nativeClass();
        var method = method(owner);
        method.instructions.insert(new InsnNode(NOP));
        for (var n : method.instructions.toArray()) {
            if (n instanceof MethodInsnNode call && call.name.equals("getItem")) {
                int local = method.maxLocals++;
                var alias = new InsnList();
                alias.add(new VarInsnNode(ASTORE, local));
                alias.add(new VarInsnNode(ALOAD, local));
                method.instructions.insert(n, alias);
            }
            if (n instanceof MethodInsnNode call && (call.name.equals("is") && call.desc.equals("(Lnet/minecraft/core/Holder;)Z")
                    || call.name.equals("hasEffect"))) {
                method.instructions.insert(n, new MethodInsnNode(INVOKESTATIC, "fixture/Peer", "observe", "(Z)Z", false));
            }
        }
        assertTrue(UndergardenGooShape.apply(owner));
        assertEquals(1, calls(owner, "goo"));
    }

    @Test
    void changedLocalOperandsOrReturnBoundaryRefuseWithoutEditing() throws Exception {
        for (String fault : new String[]{"slot", "item", "inventory", "effect", "consumer", "opaque", "signature"}) {
            var owner = nativeClass();
            var method = method(owner);
            if (fault.equals("signature")) {
                method.desc = "()V";
            }
            for (var n : method.instructions.toArray()) {
                if (n instanceof FieldInsnNode f && fault.equals("slot") && f.name.equals("FEET")) {
                    f.name = "HEAD";
                }
                if (n instanceof FieldInsnNode f && fault.equals("item") && f.name.equals("CLOGGRUM_BOOTS")) {
                    f.name = "FROSTSTEEL_BOOTS";
                }
                if (n instanceof FieldInsnNode f && fault.equals("effect") && f.name.equals("GOOEY")) {
                    f.name = "OTHER_EFFECT";
                }
                if (n instanceof MethodInsnNode c && c.name.equals("getInventory") && fault.equals("inventory")) {
                    c.name = "anotherInventory";
                }
                if (n instanceof MethodInsnNode c && c.name.equals("getItem") && fault.equals("opaque")) {
                    method.instructions.insert(n, new MethodInsnNode(INVOKESTATIC,
                                                                     "fixture/Peer",
                                                                     "unknown",
                                                                     "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
                                                                     false));
                }
                if (n instanceof JumpInsnNode j && j.getOpcode() == IFNE && fault.equals("consumer")) {
                    j.setOpcode(IFEQ);
                }
            }
            int size = method.instructions.size();
            assertFalse(UndergardenGooShape.apply(owner), fault);
            assertEquals(size, method.instructions.size(), fault);
            assertEquals(0, calls(owner, "goo"), fault);
        }
    }

    @Test
    void ambiguousPredicatesAndMissingMethodsRefuseAtomically() throws Exception {
        var owner = nativeClass();
        var method = method(owner);
        var branch = (JumpInsnNode) java.util.Arrays.stream(method.instructions.toArray())
                                                    .filter(n -> n instanceof MethodInsnNode c && c.name.equals("is") && c.desc.equals(
                                                            "(Lnet/minecraft/core/Holder;)Z"))
                                                    .findFirst().orElseThrow().getNext();
        while (branch.getOpcode() < 0) {
            branch = (JumpInsnNode) branch.getNext();
        }
        // Add a second reachable version of the exact equipment predicate, before its original receiver load.
        var start = java.util.Arrays.stream(method.instructions.toArray())
                                    .filter(n -> n instanceof MethodInsnNode c && c.name.equals("getInventory"))
                                    .findFirst()
                                    .orElseThrow()
                                    .getPrevious();
        while (start.getOpcode() < 0) {
            start = start.getPrevious();
        }
        var duplicate = new InsnList();
        for (var n = start; n != branch; n = n.getNext()) {
            if (n.getOpcode() >= 0) {
                duplicate.add(n.clone(java.util.Map.of()));
            }
        }
        duplicate.add(new JumpInsnNode(IFEQ, branch.label));
        method.instructions.insertBefore(start, duplicate);
        int size = method.instructions.size();
        assertFalse(UndergardenGooShape.apply(owner));
        assertEquals(size, method.instructions.size());
        owner = nativeClass();
        owner.methods.remove(method(owner));
        assertFalse(UndergardenGooShape.apply(owner));
    }

    private static ClassNode nativeClass() throws Exception {
        String path = System.getProperty("mismatchedboots.test.undergardenGuardJar");
        assumeTrue(path != null, "Pinned native artifact characterization is opt-in");
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(UndergardenGooShape.TARGET + ".class"))) {
            var owner = new ClassNode();
            new ClassReader(stream).accept(owner, 0);
            return owner;
        }
    }

    private static MethodNode method(ClassNode owner) {
        return owner.methods.stream().filter(m -> m.name.equals(UndergardenGooShape.METHOD)
                && m.desc.equals(UndergardenGooShape.DESCRIPTOR)).findFirst().orElseThrow();
    }

    private static long calls(ClassNode owner, String name) {
        return owner.methods.stream().flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                            .filter(n -> n instanceof MethodInsnNode call && call.name.equals(name)).count();
    }
}
