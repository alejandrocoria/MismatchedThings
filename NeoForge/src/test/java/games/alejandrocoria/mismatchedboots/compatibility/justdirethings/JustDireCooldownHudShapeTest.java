package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

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

/** Permission follows actual root/phase reads and consumed adapter output, not helper presence. */
class JustDireCooldownHudShapeTest implements Opcodes {
    @Test void nativeReadsAllowExtraWorkButRejectAnotherComponentOrParameterReceiver() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireGuardJar");
        assumeTrue(path != null, "Native JDT artifact characterization is opt-in");
        byte[] bytes;
        try (var jar = new JarFile(path); var stream = jar.getInputStream(jar.getJarEntry(JustDireCooldownHudShape.HUD + ".class"))) {
            bytes = stream.readAllBytes();
        }
        var node = read(bytes); assertTrue(JustDireCooldownHudShape.entry(node));
        render(node).instructions.insert(new InsnNode(NOP)); assertTrue(JustDireCooldownHudShape.entry(node));
        node = read(bytes);
        for (var instruction : render(node).instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("ABILITY_COOLDOWNS")) { field.name = "OTHER_COMPONENT"; }
        }
        assertFalse(JustDireCooldownHudShape.entry(node));
        node = read(bytes);
        for (var instruction : render(node).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("getAbilityParams")) {
                render(node).instructions.set(call.getPrevious().getPrevious(), new InsnNode(ACONST_NULL)); break;
            }
        }
        assertFalse(JustDireCooldownHudShape.entry(node));
    }

    @Test void actualFinalViewPublishesButDiscardedRootOrAdapterOutputDoesNot() throws Exception {
        byte[] bytes = finalBytes();
        var node = reset(bytes); assertTrue(JustDireCooldownHudShape.publish(node));
        assertEquals(ICONST_1, getter(node).instructions.getFirst().getOpcode());
        String export = System.getProperty("mismatchedboots.test.justDireCooldownHudTransformedClass");
        if (export.endsWith("-live.class")) {
            for (String variant : java.util.List.of("high", "low")) {
                var sibling = Path.of(export).resolveSibling("AbilityCooldownOverlay-" + variant + ".class");
                assertTrue(JustDireCooldownHudShape.publish(reset(Files.readAllBytes(sibling))), "Final " + variant + " operation chain must retain permission");
            }
        }
        node = reset(bytes);
        var route = renderRoute(node);
        var wrongRoot = new InsnList();
        wrongRoot.add(new InsnNode(POP)); wrongRoot.add(new InsnNode(POP)); wrongRoot.add(new InsnNode(ACONST_NULL));
        wrongRoot.add(new VarInsnNode(ALOAD, ((VarInsnNode) route.getPrevious()).var));
        render(node).instructions.insertBefore(route, wrongRoot);
        deny(node);
        node = reset(bytes); route = renderRoute(node);
        render(node).instructions.set(route.getPrevious(), new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                "Lnet/minecraft/world/entity/EquipmentSlot;"));
        deny(node);
        node = reset(bytes);
        MethodNode handler = handler(node); MethodInsnNode view = view(handler);
        var discard = new InsnList(); discard.add(new InsnNode(POP)); discard.add(new VarInsnNode(ALOAD, 1));
        handler.instructions.insert(view, discard); deny(node);
        node = reset(bytes); var method = render(node); route = renderRoute(node);
        var drop = new InsnList(); drop.add(new InsnNode(POP)); drop.add(new InsnNode(ACONST_NULL));
        method.instructions.insert(route, drop); deny(node);
    }

    @Test void unattachedForeignOrExternallyEnabledHelperNeverGrantsReadiness() throws Exception {
        byte[] bytes = finalBytes();
        var node = reset(bytes); handler(node).visibleAnnotations.clear(); deny(node);
        node = reset(bytes);
        render(node).instructions.clear(); render(node).instructions.add(new InsnNode(RETURN)); deny(node);
        node = reset(bytes);
        for (var instruction : getter(node).instructions) {
            if (instruction.getOpcode() == ICONST_0) { getter(node).instructions.set(instruction, new InsnNode(ICONST_1)); break; }
        }
        deny(node);
        node = reset(bytes); view(handler(node)).name = "anotherView"; deny(node);
    }

    private static byte[] finalBytes() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireCooldownHudTransformedClass");
        assumeTrue(path != null, "Final production HUD export is opt-in");
        return Files.readAllBytes(Path.of(path));
    }
    private static void deny(ClassNode node) {
        assertFalse(JustDireCooldownHudShape.publish(node));
        assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
    }
    private static ClassNode read(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static ClassNode reset(byte[] bytes) {
        var node = read(bytes);
        for (var instruction : getter(node).instructions) {
            if (instruction.getOpcode() == ICONST_1) { getter(node).instructions.set(instruction, new InsnNode(ICONST_0)); break; }
        }
        return node;
    }
    private static MethodNode render(ClassNode node) { return method(node, "render"); }
    private static MethodNode getter(ClassNode node) { return method(node, "mismatchedboots$justDireCooldownReady"); }
    private static MethodNode handler(ClassNode node) { return method(node, "mismatchedboots$justDireCooldownView"); }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.contains(name)).findFirst().orElseThrow();
    }
    private static MethodInsnNode renderRoute(ClassNode node) {
        for (var instruction : render(node).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.contains("mismatchedboots$justDireCooldownView")) { return call; }
        }
        throw new IllegalStateException("Missing exported production view route");
    }
    private static MethodInsnNode view(MethodNode handler) {
        for (var instruction : handler.instructions) { if (instruction instanceof MethodInsnNode call && call.name.equals("view")) { return call; } }
        throw new IllegalStateException("Missing exported HUD adapter");
    }
}
