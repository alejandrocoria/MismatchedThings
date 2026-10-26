package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JustDireJumpShapeTest implements Opcodes {
    @Test
    void finalRealMixinExtrasPairPublishesOnlyWithBothOwnedConsumers() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireTransformedClass");
        assumeTrue(path != null, "Final MixinExtras export is opt-in");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        ClassNode handler = new ClassNode();
        new ClassReader(bytes).accept(handler, 0);
        assertTrue(JustDireJumpShape.installed(handler, "jumpEvent", JustDireJumpShape.JUMP, "jump", JustDireJumpShape.PLAYER), "jump wrapper");
        assertTrue(JustDireJumpShape.installed(handler, "LivingFallDamage", JustDireJumpShape.FALL, "fall", JustDireJumpShape.SERVER_PLAYER),
                   "fall wrapper");
        var getter = JustDireJumpShape.method(handler, "mismatchedboots$justDireJumpReady", "()Z");
        getter.instructions.set(JustDireJumpShape.code(getter.instructions).getFirst(), new InsnNode(ICONST_0));
        assertTrue(JustDireJumpShape.publish(handler));
        new ClassReader(bytes).accept(handler = new ClassNode(), 0);
        handler.methods.remove(JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL));
        getter = JustDireJumpShape.method(handler, "mismatchedboots$justDireJumpReady", "()Z");
        getter.instructions.set(JustDireJumpShape.code(getter.instructions).getFirst(), new InsnNode(ICONST_0));
        assertFalse(JustDireJumpShape.publish(handler));
        assertEquals(ICONST_0, JustDireJumpShape.code(getter.instructions).getFirst().getOpcode());
    }

    @Test
    void nativeLocalContractAcceptsUnrelatedEditsButRejectsLostOrAmbiguousCompanion() throws Exception {
        try (JarFile jar = artifact()) {
            ClassNode handler = read(jar, JustDireJumpShape.HANDLER);
            assertTrue(JustDireJumpShape.entries(handler));
            MethodNode unrelated = new MethodNode(ACC_PUBLIC | ACC_STATIC, "unrelated", "()V", null, null);
            unrelated.instructions.add(new InsnNode(RETURN));
            handler.methods.add(unrelated);
            assertTrue(JustDireJumpShape.entries(handler));
            MethodNode fall = JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL);
            var read = JustDireJumpShape.feetRead(fall.desc, fall.instructions);
            fall.instructions.insertBefore(read, new FieldInsnNode(GETSTATIC, JustDireJumpShape.SLOT, "FEET", "L" + JustDireJumpShape.SLOT + ";"));
            assertFalse(JustDireJumpShape.entries(handler));
            handler = read(jar, JustDireJumpShape.HANDLER);
            handler.methods.remove(JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL));
            assertFalse(JustDireJumpShape.entries(handler));
        }
    }

    @Test
    void nativeApiAndMovementContractRejectSideEffectsForeignQueriesAndChangedAction() throws Exception {
        try (JarFile jar = artifact()) {
            ClassNode tool = read(jar, JustDireJumpShape.TOOL);
            ClassNode helper = read(jar, JustDireJumpShape.HELPERS);
            ClassNode action = read(jar, JustDireJumpShape.ACTION);
            assertTrue(JustDireJumpShape.api(tool, helper, action));
            MethodNode jump = action.methods.stream().filter(m -> m.name.equals("jumpBoost")).findFirst().orElseThrow();
            for (var n : jump.instructions) {
                if (n instanceof LdcInsnNode c && Float.valueOf(7.5f).equals(c.cst)) {
                    c.cst = 3.0f;
                }
            }
            assertFalse(JustDireJumpShape.api(tool, helper, action));
            action = read(jar, JustDireJumpShape.ACTION);
            MethodNode query = tool.methods.stream().filter(m -> m.name.equals("canUseAbility")).findFirst().orElseThrow();
            MethodInsnNode call = (MethodInsnNode) JustDireJumpShape.code(query.instructions).stream()
                                                                    .filter(n -> n instanceof MethodInsnNode).findFirst().orElseThrow();
            String original = call.owner;
            call.owner = "foreign/MutableTool";
            assertFalse(JustDireJumpShape.api(tool, helper, action));
            call.owner = original;
            query.instructions.insert(new FieldInsnNode(PUTSTATIC, "foreign/State", "changed", "Z"));
            assertFalse(JustDireJumpShape.api(tool, helper, action));
        }
    }

    @Test
    void nativePoweredResourceQueryRejectsForeignWalletReadsAndPayments() throws Exception {
        try (JarFile jar = artifact()) {
            ClassNode tool = read(jar, JustDireJumpShape.TOOL);
            ClassNode helper = read(jar, JustDireJumpShape.HELPERS);
            ClassNode action = read(jar, JustDireJumpShape.ACTION);
            assertTrue(JustDireJumpShape.api(tool, helper, action));
            MethodNode resource = JustDireJumpShape.method(helper, "testUseTool",
                                                           "(L" + JustDireJumpShape.STACK + ";L" + JustDireJumpShape.ABILITY + ";)I");
            MethodInsnNode amount = (MethodInsnNode) JustDireJumpShape.code(resource.instructions)
                                                                      .stream()
                                                                      .filter(n -> n instanceof MethodInsnNode c && c.name.equals("getAmountAsInt"))
                                                                      .findFirst()
                                                                      .orElseThrow();
            String owner = amount.owner;
            amount.owner = "foreign/MutableWallet";
            assertFalse(JustDireJumpShape.api(tool, helper, action));
            amount.owner = owner;
            assertTrue(JustDireJumpShape.api(tool, helper, action));
            amount.name = "extract";
            assertFalse(JustDireJumpShape.api(tool, helper, action));
        }
    }

    private static JarFile artifact() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireGuardJar");
        assumeTrue(path != null, "Native artifact characterization is opt-in");
        return new JarFile(path);
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode result = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(stream.readAllBytes()).accept(result, 0);
        }
        return result;
    }
}
