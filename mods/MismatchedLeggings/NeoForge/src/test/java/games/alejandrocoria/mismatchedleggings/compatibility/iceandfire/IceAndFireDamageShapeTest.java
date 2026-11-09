package games.alejandrocoria.mismatchedleggings.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceAndFireDamageShapeTest implements Opcodes {
    @Test
    void pinnedApiAndNativeConsumersAreAdmittedAndEditedOnlyOnce() throws Exception {
        var node = nativeClass(IceAndFireDamageShape.HANDLER);
        assertTrue(IceAndFireDamageShape.isCompatible(node, nativeClass(IceAndFireDamageShape.SCALE),
                nativeClass(IceAndFireDamageShape.STEEL), nativeClass(IceAndFireDamageShape.TROLL)));
        assertTrue(IceAndFireDamageShape.isLightningCompatible(node, nativeClass(IceAndFireDamageShape.ITEMS)));
        for (boolean lightning : List.of(false, true)) {
            assertTrue(IceAndFireDamageShape.apply(node, lightning));
            assertEquals(lightning ? 1 : 3, hooks(method(node, lightning)));
            new Analyzer<>(new BasicInterpreter()).analyze(node.name, method(node, lightning));
            assertFalse(IceAndFireDamageShape.apply(node, lightning));
        }
    }

    @Test
    void bootsActualLocalSelectorsRemainReadyInEitherEditOrder() throws Exception {
        var boots = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireDamageShape");
        for (boolean lightning : List.of(false, true)) {
            var select = boots.getMethod(lightning ? "lightningFeetChecks" : "feetChecks", InsnList.class);
            for (boolean legsFirst : List.of(false, true)) {
                var node = nativeClass(IceAndFireDamageShape.HANDLER);
                if (legsFirst) { assertTrue(IceAndFireDamageShape.apply(node, lightning)); }
                var selected = (List<?>) select.invoke(null, method(node, lightning).instructions);
                assertEquals(lightning ? 1 : 3, selected.size());
                // Native predicates remain in a sibling result-extension chain; no sibling handler identity is assumed.
                for (var predicate : selected) {
                    method(node, lightning).instructions.insert((AbstractInsnNode) predicate,
                            new MethodInsnNode(INVOKESTATIC, "fixture/Feet", "result", "(Z)Z", false));
                }
                if (!legsFirst) { assertTrue(IceAndFireDamageShape.apply(node, lightning)); }
                new Analyzer<>(new BasicInterpreter()).analyze(node.name, method(node, lightning));
            }
        }
    }

    @Test
    void relocationAndUnrelatedExecutableOtherSlotEditsAreAccepted() throws Exception {
        for (boolean lightning : List.of(false, true)) {
            var node = nativeClass(IceAndFireDamageShape.HANDLER);
            var method = method(node, lightning);
            for (var instruction : method.instructions) {
                if (instruction instanceof VarInsnNode local && local.var > 0) { local.var += 8; }
            }
            method.maxLocals += 8;
            method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Other", "observe", "()V", false));
            method.instructions.insert(new InsnNode(NOP));
            var unrelated = new InsnList();
            unrelated.add(new VarInsnNode(ALOAD, 0));
            unrelated.add(new MethodInsnNode(INVOKEVIRTUAL, "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre", "getEntity",
                    "()Lnet/minecraft/world/entity/LivingEntity;", false));
            unrelated.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "LEGS", "Lnet/minecraft/world/entity/EquipmentSlot;"));
            unrelated.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                    "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
            unrelated.add(new InsnNode(POP));
            method.instructions.insert(unrelated);
            assertTrue(IceAndFireDamageShape.apply(node, lightning));
            new Analyzer<>(new BasicInterpreter()).analyze(node.name, method);
        }
    }

    @Test
    void missingAmbiguousOrUnsafeLocalPredicatesRefuseAtomicallyAndIndependently() throws Exception {
        for (boolean lightning : List.of(false, true)) {
            for (String fault : List.of("entity", "slot", "operand", "predicate", "ambiguous", "missing", "consumer", "branch", "sink", "event", "overwrite", "damageType")) {
                if (!lightning && fault.equals("damageType") || lightning && fault.equals("overwrite")) { continue; }
                var node = nativeClass(IceAndFireDamageShape.HANDLER);
                var method = method(node, lightning);
                var slot = Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof FieldInsnNode f && f.name.equals("LEGS"))
                        .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
                var predicate = (lightning ? IceAndFireDamageShape.lightningLegsChecks(method.instructions)
                        : IceAndFireDamageShape.legsChecks(method.instructions)).getFirst();
                switch (fault) {
                    case "entity" -> ((VarInsnNode) previous(slot)).var = 0;
                    case "slot" -> slot.name = "HEAD";
                    case "operand" -> {
                        if (lightning) {
                            Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof FieldInsnNode f && f.name.equals("DRAGONSTEEL_LIGHTNING_LEGGINGS"))
                                    .map(FieldInsnNode.class::cast).findFirst().orElseThrow().name = "DRAGONSTEEL_FIRE_LEGGINGS";
                        } else { ((TypeInsnNode) predicate).desc = IceAndFireDamageShape.SCALE; }
                    }
                    case "predicate" -> method.instructions.insert(predicate, new MethodInsnNode(INVOKESTATIC, "fixture/Unknown", "result", "(Z)Z", false));
                    case "ambiguous" -> {
                        var copies = new java.util.HashMap<LabelNode, LabelNode>();
                        var duplicate = new InsnList();
                        for (var instruction = previous(slot); ; instruction = instruction.getNext()) {
                            if (instruction.getOpcode() >= 0) { duplicate.add(instruction.clone(copies)); }
                            if (instruction == predicate) { break; }
                        }
                        duplicate.add(new InsnNode(POP));
                        method.instructions.insert(duplicate);
                    }
                    case "missing" -> method.instructions.remove(predicate);
                    case "branch" -> ((JumpInsnNode) next(predicate)).setOpcode(IFNE);
                    case "consumer" -> {
                        if (lightning) {
                            var zero = Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() == FCONST_0).findFirst().orElseThrow();
                            method.instructions.set(zero, new InsnNode(FCONST_1));
                        } else {
                            var subtraction = next(next(next(predicate)));
                            // Troll: predicate, IFEQ, FLOAD, LDC, FSUB.
                            subtraction = next(subtraction);
                            assertEquals(FSUB, subtraction.getOpcode());
                            method.instructions.set(subtraction, new InsnNode(FADD));
                        }
                    }
                    case "sink" -> {
                        if (lightning) {
                            Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call && call.name.equals("setNewDamage"))
                                    .map(MethodInsnNode.class::cast).findFirst().orElseThrow().name = "otherConsumer";
                        } else {
                            var multiply = Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() == FMUL).findFirst().orElseThrow();
                            method.instructions.set(multiply, new InsnNode(FADD));
                        }
                    }
                    case "damageType" -> Arrays.stream(method.instructions.toArray())
                            .filter(n -> n instanceof FieldInsnNode f && f.name.equals("LIGHTNING_BOLT"))
                            .map(FieldInsnNode.class::cast).findFirst().orElseThrow().name = "MAGIC";
                    case "overwrite" -> {
                        var multiplication = Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() == FMUL).findFirst().orElseThrow();
                        int coefficient = ((VarInsnNode) previous(multiplication)).var;
                        var load = previous(previous(multiplication));
                        method.instructions.insertBefore(load, new InsnNode(FCONST_1));
                        method.instructions.insertBefore(load, new VarInsnNode(FSTORE, coefficient));
                    }
                    case "event" -> {
                        method.instructions.insert(new VarInsnNode(ASTORE, 0));
                        method.instructions.insert(new InsnNode(ACONST_NULL));
                    }
                }
                int size = method.instructions.size();
                assertFalse(IceAndFireDamageShape.apply(node, lightning), lightning + "/" + fault);
                assertEquals(size, method.instructions.size());
                assertEquals(0, hooks(method));
                assertTrue(IceAndFireDamageShape.apply(node, !lightning), "independent consumer: " + fault);
            }
        }
    }

    @Test
    void incompleteOptionalApiDeclinesOnlyItsOwnAdmission() throws Exception {
        var node = nativeClass(IceAndFireDamageShape.HANDLER);
        var scale = nativeClass(IceAndFireDamageShape.SCALE);
        scale.superName = "fixture/ChangedBase";
        assertFalse(IceAndFireDamageShape.isCompatible(node, scale, nativeClass(IceAndFireDamageShape.STEEL), nativeClass(IceAndFireDamageShape.TROLL)));
        assertTrue(IceAndFireDamageShape.isLightningCompatible(node, nativeClass(IceAndFireDamageShape.ITEMS)));
        var items = nativeClass(IceAndFireDamageShape.ITEMS);
        items.fields.removeIf(f -> f.name.equals("DRAGONSTEEL_LIGHTNING_LEGGINGS"));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(node, items));
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (var jar = new JarFile(System.getProperty("mismatchedleggings.test.iceAndFireGuardJar"));
             var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node, boolean lightning) {
        return node.methods.stream().filter(m -> m.name.equals(lightning ? "onLivingHurt" : "onEntityDamage")).findFirst().orElseThrow();
    }
    private static long hooks(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                && call.owner.equals(IceAndFireDamageShape.ADAPTER)).count();
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && (node.getOpcode() < 0 || node.getOpcode() == NOP)); return node;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0); return node;
    }
}
