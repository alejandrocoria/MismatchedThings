package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JustDireArmorWearShapeTest implements Opcodes {
    @Test void nativePostEventConsumerIsEligibleButCannotPublishPermission() throws Exception {
        var node = nativeClass();
        assertTrue(JustDireArmorWearShape.entry(node));
        assertFalse(JustDireArmorWearShape.publish(node));
        for (var instruction : consumer(node).instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("newDamage")) { field.name = "originalDamage"; }
        }
        assertFalse(JustDireArmorWearShape.entry(node));
    }

    @Test void actualFinalExportRequiresItsOwnIntactPublicationAndRouting() throws Exception {
        var node = transformed();
        consumer(node).instructions.insert(new LabelNode());
        assertTrue(JustDireArmorWearShape.publish(node));
        assertEquals(ICONST_1, getter(node).instructions.getFirst().getOpcode());
        assertFalse(JustDireArmorWearShape.publish(node), "an already true/foreign-edited getter is not a proof");
        assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
    }

    @Test void cancellationAndActualMapConsumerAssociationAreRequired() throws Exception {
        var cancel = nativeClass();
        for (var method : cancel.methods) {
            if (!method.name.equals("onArmorHurt")) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("isCanceled")) { call.name = "unrelatedFlag"; }
            }
        }
        assertFalse(JustDireArmorWearShape.entry(cancel));
        var association = nativeClass();
        for (var method : association.methods) {
            if (!method.name.equals("onArmorHurt")) { continue; }
            for (var instruction : method.instructions) {
                if (!(instruction instanceof InvokeDynamicInsnNode indy)) { continue; }
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (indy.bsmArgs[i] instanceof Handle handle && handle.getName().equals(JustDireArmorWearShape.LAMBDA)) {
                        indy.bsmArgs[i] = new Handle(handle.getTag(), handle.getOwner(), "unrelatedConsumer", handle.getDesc(), false);
                    }
                }
            }
        }
        assertFalse(JustDireArmorWearShape.entry(association));
    }

    @Test void wrongFinalAmountOrProtectedEntryAndForeignGetterRemainDisabled() throws Exception {
        for (String fieldName : java.util.List.of("newDamage", "originalDamage")) {
            var node = transformed();
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.owner.endsWith("ArmorHurtEvent$ArmorEntry") && field.name.equals(fieldName)) {
                        field.name = fieldName.equals("newDamage") ? "originalDamage" : "newDamage";
                    }
                }
            }
            assertFalse(JustDireArmorWearShape.publish(node), fieldName);
            assertEquals(ICONST_0, getter(node).instructions.getFirst().getOpcode());
        }
        var foreign = transformed(); getter(foreign).visibleAnnotations.clear();
        assertFalse(JustDireArmorWearShape.publish(foreign));
    }

    @Test void auditedWearPriceUsesOneIntegerRequestAndNativeLinearDiscount() {
        assertEquals(200, JustDireArmorWearCompatibility.bill(2, 0));
        assertEquals(180, JustDireArmorWearCompatibility.bill(2, 1));
        assertEquals(140, JustDireArmorWearCompatibility.bill(2, 3));
        assertEquals(0, JustDireArmorWearCompatibility.bill(2, 10));
        assertEquals(0, JustDireArmorWearCompatibility.bill(0, 0));
        assertEquals(-1, JustDireArmorWearCompatibility.bill(Integer.MAX_VALUE, 0));
        assertEquals(-1, JustDireArmorWearCompatibility.bill(2, -1));
    }
    private static ClassNode nativeClass() throws Exception {
        var node = new ClassNode();
        try (var stream = JustDireArmorWearShapeTest.class.getClassLoader().getResourceAsStream(JustDireArmorWearShape.OWNER + ".class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static ClassNode transformed() throws Exception {
        String path = System.getProperty("mismatchedboots.test.justDireArmorWearTransformedClass", "");
        assumeTrue(!path.isEmpty(), "Supply an actual runtime export for final-shape acceptance");
        var node = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(path))).accept(node, 0);
        // Reconstruct the state immediately before our final publication callback.
        for (var instruction : getter(node).instructions) {
            if (instruction.getOpcode() >= 0) { getter(node).instructions.set(instruction, new InsnNode(ICONST_0)); break; }
        }
        return node;
    }
    private static MethodNode consumer(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals(JustDireArmorWearShape.LAMBDA)).findFirst().orElseThrow(); }
    private static MethodNode getter(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals(JustDireArmorWearShape.GETTER)).findFirst().orElseThrow(); }
}
