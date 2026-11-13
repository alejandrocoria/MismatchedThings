package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JustDireCooldownHudShapeTest implements Opcodes {
    @Test
    void nativeFlowAllowsExtraWorkButNotUnrelatedClockData() throws Exception {
        byte[] bytes;
        try (var jar = new JarFile(System.getProperty("mismatchedleggings.test.jdtJar"));
             var input = jar.getInputStream(jar.getJarEntry(JustDireCooldownHudShape.HUD + ".class"))) {
            bytes = input.readAllBytes();
        }
        var node = read(bytes);
        assertTrue(JustDireCooldownHudShape.entry(node));
        method(node, "render").instructions.insert(new InsnNode(NOP));
        assertTrue(JustDireCooldownHudShape.entry(node));
        for (var instruction : method(node, "render").instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("ABILITY_COOLDOWNS")) {
                field.name = "FOREIGN_COMPONENT";
            }
        }
        assertFalse(JustDireCooldownHudShape.entry(node));
    }

    @Test
    void actualStackedWrapperRequiresOwnedOriginalSlotAndConsumedOutput() throws Exception {
        String path = System.getProperty("mismatchedleggings.test.jdtHudClass");
        assumeTrue(path != null, "Pass a real connected HUD export for final-hook validation");
        byte[] bytes = Files.readAllBytes(Path.of(path));
        var node = reset(bytes);
        assertTrue(JustDireCooldownHudShape.publish(node));
        method(node, "render").instructions.insert(new InsnNode(NOP));
        ready(node).instructions.set(ready(node).instructions.getFirst(), new InsnNode(ICONST_0));
        assertTrue(JustDireCooldownHudShape.publish(node));

        node = reset(bytes);
        for (var bridge : node.methods) {
            if (bridge.name.startsWith("mixinextras$bridge$")) {
                bridge.maxStack = 0;
                bridge.maxLocals = 0;
            }
        }
        assertTrue(JustDireCooldownHudShape.publish(node), "Generated bridge maxs are not finalized at the export boundary");
        for (var bridge : node.methods) {
            if (bridge.name.startsWith("mixinextras$bridge$")) {
                assertEquals(0, bridge.maxStack);
                assertEquals(0, bridge.maxLocals);
            }
        }

        node = reset(bytes);
        var view = view(node);
        handler(node).instructions.set(view.getPrevious(), new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                "Lnet/minecraft/world/entity/EquipmentSlot;"));
        deny(node);

        node = reset(bytes);
        for (var instruction : handler(node).instructions) {
            if (instruction.getOpcode() == AASTORE && instruction.getPrevious().getOpcode() == ALOAD
                    && ((VarInsnNode) instruction.getPrevious()).var == 2) {
                handler(node).instructions.set(instruction.getPrevious(), new FieldInsnNode(GETSTATIC,
                        "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;"));
                break;
            }
        }
        deny(node);

        node = reset(bytes);
        var discard = new InsnList();
        discard.add(new InsnNode(POP));
        discard.add(new InsnNode(ACONST_NULL));
        handler(node).instructions.insert(view(node), discard);
        deny(node);

        node = reset(bytes);
        handler(node).visibleAnnotations.clear();
        deny(node);
        node = reset(bytes);
        ready(node).instructions.set(ready(node).instructions.getFirst(), new InsnNode(ICONST_1));
        deny(node);
        node = reset(bytes);
        method(node, "render").instructions.clear();
        method(node, "render").instructions.add(new InsnNode(RETURN));
        deny(node);
    }

    private static void deny(ClassNode node) {
        assertFalse(JustDireCooldownHudShape.publish(node));
        assertEquals(ICONST_0, ready(node).instructions.getFirst().getOpcode());
    }

    private static ClassNode read(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static ClassNode reset(byte[] bytes) {
        var node = read(bytes);
        for (var instruction : ready(node).instructions) {
            if (instruction.getOpcode() == ICONST_1) {
                ready(node).instructions.set(instruction, new InsnNode(ICONST_0));
                break;
            }
        }
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.contains(name)).findFirst().orElseThrow();
    }

    private static MethodNode ready(ClassNode node) {
        return method(node, "mismatchedleggings$justDireCooldownReady");
    }
    private static MethodNode handler(ClassNode node) {
        return method(node, "mismatchedleggings$justDireCooldownView");
    }
    private static MethodInsnNode view(ClassNode node) {
        for (var instruction : handler(node).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("view")) {
                return call;
            }
        }
        throw new IllegalStateException("Missing real HUD view call");
    }

}
