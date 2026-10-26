package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JustDireNegateFallShapeTest implements Opcodes {
    @Test
    void nativeSpendingContractIsLocalAndIndependentFromJump() throws Exception {
        try (JarFile jar = artifact()) {
            ClassNode handler = read(jar, JustDireJumpShape.HANDLER);
            assertTrue(JustDireNegateFallShape.flow(handler, false));
            MethodNode unrelated = new MethodNode(ACC_PUBLIC | ACC_STATIC, "unrelated", "()V", null, null);
            unrelated.instructions.add(new InsnNode(RETURN));
            handler.methods.add(unrelated);
            assertTrue(JustDireNegateFallShape.flow(handler, false));
            MethodNode fall = JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL);
            for (var node : fall.instructions) {
                if (node instanceof FieldInsnNode field && field.name.equals("NEGATEFALLDAMAGE")) {
                    field.name = "JUMPBOOST";
                    break;
                }
            }
            assertFalse(JustDireNegateFallShape.flow(handler, false));
            assertTrue(JustDireJumpShape.entries(handler), "Negate-only rejection preserves the passive entry");
            handler = read(jar, JustDireJumpShape.HANDLER);
            fall = JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL);
            for (var node : fall.instructions) {
                if (node.getOpcode() == D2I) {
                    fall.instructions.set(node, new InsnNode(DNEG));
                    break;
                }
            }
            assertFalse(JustDireNegateFallShape.flow(handler, false), "Changed native payment multiplier");
            handler = read(jar, JustDireJumpShape.HANDLER);
            fall = JustDireJumpShape.method(handler, "LivingFallDamage", JustDireJumpShape.FALL);
            var feetRead = JustDireJumpShape.feetRead(fall.desc, fall.instructions);
            var feet = feetRead.getPrevious();
            while (feet.getOpcode() < 0) {
                feet = feet.getPrevious();
            }

            var player = feet.getPrevious();
            while (player.getOpcode() < 0) {
                player = player.getPrevious();
            }

            LabelNode bypass = new LabelNode();
            fall.instructions.insertBefore(player, bypass);
            for (var node : fall.instructions) {
                if (node instanceof MethodInsnNode call && call.name.equals("getMainHandItem")) {
                    fall.instructions.insertBefore(call.getPrevious(), new JumpInsnNode(GOTO, bypass));
                    break;
                }
            }
            assertFalse(JustDireNegateFallShape.flow(handler, false), "A prelude cannot bypass native hand precedence");
        }
    }

    @Test
    void nativeCostQueryMustRemainReadOnlyWithoutPoisoningJump() throws Exception {
        try (JarFile jar = artifact()) {
            ClassNode tool = read(jar, JustDireJumpShape.TOOL);
            ClassNode ability = read(jar, JustDireJumpShape.ABILITY);
            assertTrue(JustDireNegateFallShape.api(tool, ability));
            JustDireJumpShape.method(ability, "getFeCost", "()I").instructions
                    .insert(new FieldInsnNode(PUTSTATIC, "foreign/State", "changed", "Z"));
            assertFalse(JustDireNegateFallShape.api(tool, ability));
            assertTrue(JustDireJumpShape.api(tool, read(jar, JustDireJumpShape.HELPERS), read(jar, JustDireJumpShape.ACTION)));
        }
    }

    @Test
    void finalRealExportRequiresItsOwnedFalseStubAndExactPermissionArgument() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireTransformedClass");
        assumeTrue(path != null, "Final MixinExtras export is opt-in");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        ClassNode handler = read(bytes);
        reset(handler);
        assertTrue(JustDireNegateFallShape.publish(handler, true), "Actual transformed local flow");
        handler = read(bytes);
        reset(handler);
        assertFalse(JustDireNegateFallShape.publish(handler, false));
        assertEquals(ICONST_0,
                     JustDireJumpShape.code(JustDireJumpShape.method(handler, JustDireNegateFallShape.GETTER, "()Z").instructions)
                                      .getFirst()
                                      .getOpcode());
        handler = read(bytes);
        reset(handler);
        for (MethodNode method : handler.methods) {
            for (var node : method.instructions) {
                if (node instanceof MethodInsnNode call && call.name.equals(JustDireNegateFallShape.GETTER)) {
                    method.instructions.set(call, new InsnNode(ICONST_1));
                }
            }
        }
        assertFalse(JustDireNegateFallShape.publish(handler, true), "A forced permission argument is not an owned read");
        handler = read(bytes);
        JustDireJumpShape.method(handler, JustDireNegateFallShape.GETTER, "()Z").instructions
                .set(JustDireJumpShape.method(handler, JustDireNegateFallShape.GETTER, "()Z").instructions.getFirst(), new InsnNode(ICONST_1));
        assertFalse(JustDireNegateFallShape.publish(handler, true), "An externally altered getter fails closed");
    }

    private static void reset(ClassNode handler) {
        var getter = JustDireJumpShape.method(handler, JustDireNegateFallShape.GETTER, "()Z");
        getter.instructions.set(JustDireJumpShape.code(getter.instructions).getFirst(), new InsnNode(ICONST_0));
    }

    private static JarFile artifact() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireGuardJar");
        assumeTrue(path != null, "Native artifact characterization is opt-in");
        return new JarFile(path);
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            return read(stream.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }
}
