package games.alejandrocoria.mismatchedboots.compatibility.aether;

import games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AetherTooltipShapeTest {
    @Test
    void nativeProducerCallsAreGuardedIndependentlyAndExactlyOnce() throws Exception {
        var owner = nativeEvents();
        var peer = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "foreignObservation", "()V", null, null);
        peer.instructions.add(new InsnNode(Opcodes.RETURN)); owner.methods.add(peer);
        for (boolean attributes : new boolean[]{true, false}) {
            assertTrue(AetherTooltipShape.producer(owner, attributes));
            assertFalse(AetherTooltipShape.producer(owner, attributes), "No duplicate insertion");
        }
        assertEquals(2, calls(owner, AetherTooltipShape.HOOKS), "Original native producers remain, behind their individual conditions");
    }

    @Test
    void finalArgumentChainsAndUnrelatedEditsDoNotRequireWholeMethodIdentity() throws Exception {
        var owner = nativeEvents();
        for (var method : owner.methods) {
            if (!method.name.equals("onAddAttributeTooltips") && !method.name.equals("onAddTooltipsLowest")) { continue; }
            method.instructions.insert(new InsnNode(Opcodes.NOP));
            var observation = new InsnList();
            observation.add(new MethodInsnNode(Opcodes.INVOKESTATIC, AetherTooltipShape.STATE, "tiersReady", "()Z", false));
            observation.add(new InsnNode(Opcodes.POP)); method.instructions.insert(observation);
            for (var node : method.instructions.toArray()) {
                if (node instanceof MethodInsnNode call && (call.name.equals("getStack") || call.name.equals("getItemStack"))) {
                    method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Peer", "identity",
                            "(" + AetherTooltipShape.STACK + ")" + AetherTooltipShape.STACK, false));
                }
            }
        }
        assertTrue(AetherTooltipShape.producer(owner, true)); assertTrue(AetherTooltipShape.producer(owner, false));
    }

    @Test
    void opaqueEventSourcesRefuseOnlyTheirOwnProducer() throws Exception {
        for (boolean attributes : new boolean[]{true, false}) {
            var owner = nativeEvents();
            var method = owner.methods.stream().filter(m -> m.name.equals(attributes ? "onAddAttributeTooltips" : "onAddTooltipsLowest")).findFirst().orElseThrow();
            for (var node : method.instructions) {
                if (node instanceof MethodInsnNode call && call.name.equals(attributes ? "getStack" : "getItemStack")) {
                    call.name = "foreignStack";
                }
            }
            assertFalse(AetherTooltipShape.producer(owner, attributes));
            assertTrue(AetherTooltipShape.producer(owner, !attributes));
        }
    }

    @Test
    void bothOwnedDetailBranchesAreScopedAtomicallyWithoutChangingCommonSource() throws Exception {
        var owner = formatter();
        assertTrue(AetherTooltipShape.sourceScope(owner)); assertFalse(AetherTooltipShape.sourceScope(owner));
        assertEquals(2, calls(owner, AetherTooltipShape.STATE));
        var changed = formatter();
        for (var method : changed.methods) {
            for (var node : method.instructions) {
                if (node instanceof MethodInsnNode call && call.name.equals("addDetailsToTooltip")) { call.name = "foreignDetails"; break; }
            }
        }
        assertFalse(AetherTooltipShape.sourceScope(changed));
        assertEquals(0, calls(changed, AetherTooltipShape.STATE), "One unproved branch must not leave the other partially scoped");
    }

    private static long calls(ClassNode owner, String target) {
        return owner.methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(node -> node instanceof MethodInsnNode call && call.owner.equals(target)).count();
    }
    private static ClassNode nativeEvents() throws Exception {
        String path = System.getProperty("mismatchedboots.test.aetherGuardJar");
        assumeTrue(path != null, "Native Aether characterization is opt-in");
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(AetherTooltipShape.EVENTS + ".class"))) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static ClassNode formatter() throws Exception {
        try (var stream = MixedBootsTooltipFormatter.class.getResourceAsStream("MixedBootsTooltipFormatter.class")) {
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
}
