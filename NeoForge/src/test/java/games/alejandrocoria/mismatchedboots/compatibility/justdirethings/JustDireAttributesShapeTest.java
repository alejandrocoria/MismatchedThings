package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression shapes exercise permission, not ASM/Java's own implementation. */
class JustDireAttributesShapeTest implements Opcodes {
    private static final String DESC = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/component/ItemAttributeModifiers;)Lnet/minecraft/world/item/component/ItemAttributeModifiers;";
    private static final String ROUTE = "mismatchedboots$justDireAttributes";

    @Test void nativeEntryAndOwnedRoutingPublishOnlyAfterFinalProof() throws Exception {
        var original = nativeClass();
        assertTrue(JustDireAttributesShape.entry(original));
        assertFalse(JustDireAttributesShape.publish(original));
        var routed = routed();
        assertTrue(JustDireAttributesShape.publish(routed));
        assertEquals(ICONST_1, getter(routed).instructions.getFirst().getOpcode());
    }

    @Test void harmlessDebugAndEarlierDefaultTransformRetainRouting() throws Exception {
        var node = routed();
        var method = entry(node);
        method.instructions.insert(new LabelNode());
        var route = route(method);
        method.instructions.insertBefore(route, new MethodInsnNode(INVOKESTATIC, "fixture/OtherMod", "edit",
                "(Lnet/minecraft/world/item/component/ItemAttributeModifiers;)Lnet/minecraft/world/item/component/ItemAttributeModifiers;", false));
        assertTrue(JustDireAttributesShape.publish(node));
    }

    @Test void wrongRootDefaultsOrDiscardedRoutingDenyPermission() throws Exception {
        for (boolean wrongRoot : List.of(false, true)) {
            var node = routed(); var route = route(entry(node));
            var load = route.getPrevious();
            if (wrongRoot) { load = load.getPrevious(); }
            entry(node).instructions.set(load, new InsnNode(ACONST_NULL));
            assertFalse(JustDireAttributesShape.publish(node));
            assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
        }
        var node = routed(); var method = entry(node);
        var replacement = new InsnList(); replacement.add(new InsnNode(POP)); replacement.add(new VarInsnNode(ALOAD, 1));
        method.instructions.insert(route(method), replacement);
        assertFalse(JustDireAttributesShape.publish(node));
    }

    @Test void foreignRoutingGetterEditsAndDiscardedEventResultDenyPermission() throws Exception {
        var foreign = routed(); foreign.methods.stream().filter(m -> m.name.equals(ROUTE)).findFirst().orElseThrow().visibleAnnotations.clear();
        assertFalse(JustDireAttributesShape.publish(foreign));
        var edited = routed(); getter(edited).instructions.set(getter(edited).instructions.getFirst(), new InsnNode(ICONST_1));
        assertFalse(JustDireAttributesShape.publish(edited));
        assertEquals(ICONST_0, getter(edited).instructions.getFirst().getOpcode());
        var discarded = routed(); var method = entry(discarded);
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() == ARETURN) {
                var replacement = new InsnList(); replacement.add(new InsnNode(POP)); replacement.add(new VarInsnNode(ALOAD, 1));
                method.instructions.insertBefore(instruction, replacement); break;
            }
        }
        assertFalse(JustDireAttributesShape.publish(discarded));
    }

    private static ClassNode nativeClass() throws Exception {
        var node = new ClassNode();
        try (var stream = JustDireAttributesShapeTest.class.getClassLoader().getResourceAsStream(JustDireAttributesShape.OWNER + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static ClassNode routed() throws Exception {
        var node = nativeClass(); var method = entry(node);
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("<init>")
                    && call.owner.endsWith("ItemAttributeModifierEvent")) {
                var defaults = call.getPrevious();
                method.instructions.insertBefore(defaults, new VarInsnNode(ALOAD, 0));
                method.instructions.insertBefore(call, new MethodInsnNode(INVOKESTATIC, node.name, ROUTE, DESC, false)); break;
            }
        }
        method.maxStack += 2;
        var router = new MethodNode(ACC_PRIVATE | ACC_STATIC, ROUTE, DESC, null, null);
        router.instructions.add(new VarInsnNode(ALOAD, 0)); router.instructions.add(new VarInsnNode(ALOAD, 1));
        router.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireAttributesCompatibility", "project", DESC, false));
        router.instructions.add(new InsnNode(ARETURN));
        router.maxStack = 2; router.maxLocals = 2; owned(router); node.methods.add(router);
        var getter = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$justDireAttributesReady", "()Z", null, null);
        getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN)); getter.maxStack = 1;
        owned(getter); node.methods.add(getter);
        return node;
    }
    private static void owned(MethodNode method) {
        var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", "games.alejandrocoria.mismatchedboots.mixin.JustDireAttributesMixin");
        method.visibleAnnotations = new java.util.ArrayList<>(List.of(annotation));
    }
    private static MethodNode entry(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("computeModifiedAttributes")).findFirst().orElseThrow(); }
    private static MethodNode getter(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("mismatchedboots$justDireAttributesReady")).findFirst().orElseThrow(); }
    private static MethodInsnNode route(MethodNode method) {
        for (var instruction : method.instructions) { if (instruction instanceof MethodInsnNode call && call.name.equals(ROUTE)) { return call; } }
        throw new IllegalStateException("Missing fixture router");
    }
}
