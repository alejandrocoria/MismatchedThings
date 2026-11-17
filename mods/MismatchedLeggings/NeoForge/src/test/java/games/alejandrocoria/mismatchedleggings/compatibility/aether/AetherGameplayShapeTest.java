package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AetherGameplayShapeTest implements Opcodes {
    @Test
    void recordNullLaneAndSiblingOperandProvenanceAreRequired() throws Exception {
        for (String fault : List.of("gate-value", "gate-target", "accessor", "requested", "peer-stack", "peer-wearer")) {
            var node = nativeClass(AetherSetShape.EQUIPMENT);
            var count = consumer(node);
            var gates = Arrays.stream(count.instructions.toArray()).filter(n -> n.getOpcode() == IFNULL)
                    .map(JumpInsnNode.class::cast).toList();
            assertEquals(1, gates.size(), "The pinned native input must exercise the record/null contract");
            var boots = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.aether.AetherRecognitionShape")
                    .getDeclaredMethod("apply", ClassNode.class);
            boots.setAccessible(true);
            assertTrue((boolean) boots.invoke(null, node));
            var bridge = Arrays.stream(count.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                    && call.name.equals("setComponent")).map(MethodInsnNode.class::cast).findFirst().orElseThrow();
            switch (fault) {
                case "gate-value" -> count.instructions.insertBefore(gates.getFirst(), new MethodInsnNode(INVOKESTATIC,
                        "fixture/Foreign", "value", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                case "gate-target" -> gates.getFirst().label = Arrays.stream(count.instructions.toArray())
                        .filter(n -> n instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ)
                        .map(JumpInsnNode.class::cast).findFirst().orElseThrow().label;
                case "accessor" -> Arrays.stream(count.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                        && call.name.equals("armor")).map(MethodInsnNode.class::cast).findFirst().orElseThrow().name = "other";
                case "requested" -> ((VarInsnNode) previous(previous(previous(bridge)))).var = 0;
                case "peer-stack" -> {
                    count.instructions.insertBefore(previous(bridge), new MethodInsnNode(INVOKESTATIC, "fixture/Foreign", "stack",
                            "(L" + AetherSetShape.STACK + ";)L" + AetherSetShape.STACK + ";", false));
                }
                case "peer-wearer" -> count.instructions.insertBefore(bridge, new MethodInsnNode(INVOKESTATIC, "fixture/Foreign", "wearer",
                        "(L" + AetherSetShape.ENTITY + ";)L" + AetherSetShape.ENTITY + ";", false));
            }
            assertFalse(AetherSetShape.apply(node), fault);
            assertEquals(0, adapters(count), "No partial sibling hook: " + fault);
        }
    }

    @Test
    void actualBootsGameplayEditsComposeInBothOrders() throws Exception {
        for (String target : targets()) {
            var shape = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.aether."
                    + (target.equals(AetherSetShape.EQUIPMENT) ? "AetherRecognitionShape" : "AetherAlkahestShape"));
            var boots = shape.getDeclaredMethod("apply", ClassNode.class);
            boots.setAccessible(true);
            for (boolean bootsFirst : List.of(false, true)) {
                for (boolean wrapped : List.of(false, true)) {
                    var node = nativeClass(target);
                    if (wrapped && target.equals(AetherSetShape.EQUIPMENT)) {
                        var query = Arrays.stream(consumer(node).instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                                && call.owner.equals(AetherSetShape.STACK) && call.name.equals("get")).findFirst().orElseThrow();
                        for (String name : List.of("fixture$first", "fixture$second")) {
                            var identity = new MethodNode(ACC_PRIVATE | ACC_STATIC, name, "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                            identity.instructions.add(new VarInsnNode(ALOAD, 0));
                            identity.instructions.add(new InsnNode(ARETURN));
                            identity.maxLocals = 1;
                            identity.maxStack = 1;
                            node.methods.add(identity);
                            consumer(node).instructions.insert(query, new MethodInsnNode(INVOKESTATIC, node.name, name, identity.desc, false));
                        }
                    }
                    if (bootsFirst) { assertTrue((boolean) boots.invoke(null, node), target); }
                    assertTrue(apply(node), target);
                    if (!bootsFirst) { assertTrue((boolean) boots.invoke(null, node), target); }
                    var method = consumer(node);
                    new Analyzer<>(new BasicInterpreter()).analyze(node.name, method);
                    assertTrue(Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode call
                            && call.owner.contains("/mismatchedboots/")), target);
                    assertTrue(Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode call
                            && call.owner.contains("/mismatchedleggings/")), target);
                }
            }
        }
    }

    @Test
    void nativeGuardIsSingleUseAndAllowsObservationsAndProvenIdentityReturn() throws Exception {
        for (String target : targets()) {
            var node = nativeClass(target);
            consumer(node).instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "note", "()V", false));
            if (target.equals(AetherSetShape.EQUIPMENT)) {
                var helper = new MethodNode(ACC_STATIC, "fixture$count", "(I)I", null, null);
                helper.instructions.add(new VarInsnNode(ILOAD, 0));
                helper.instructions.add(new InsnNode(IRETURN));
                helper.maxLocals = 1;
                helper.maxStack = 1;
                node.methods.add(helper);
                consumer(node).instructions.insertBefore(last(consumer(node), IRETURN),
                        new MethodInsnNode(INVOKESTATIC, node.name, helper.name, helper.desc, false));
            }
            assertTrue(apply(node), target);
            assertFalse(apply(node), "Do not add a second contribution: " + target);
        }
    }

    @Test
    void setCountRefusesChangedEligibilityEnumerationAndResultsBeforeEditing() throws Exception {
        for (String fault : List.of("scale", "clamp", "seed", "increment", "component", "group", "type", "slot", "filter")) {
            var node = nativeClass(AetherSetShape.EQUIPMENT);
            var count = consumer(node);
            var equipment = node.methods.stream().filter(m -> m.name.equals("getEquipment")).findFirst().orElseThrow();
            switch (fault) {
                case "scale" -> {
                    var ret = last(count, IRETURN);
                    count.instructions.insertBefore(ret, new InsnNode(ICONST_2));
                    count.instructions.insertBefore(ret, new InsnNode(IMUL));
                }
                case "clamp" -> {
                    var ret = last(count, IRETURN);
                    count.instructions.insertBefore(ret, new InsnNode(ICONST_3));
                    count.instructions.insertBefore(ret, new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
                }
                case "seed" -> count.instructions.set(Arrays.stream(count.instructions.toArray())
                        .filter(n -> n.getOpcode() == ICONST_0).findFirst().orElseThrow(), new InsnNode(ICONST_1));
                case "increment" -> Arrays.stream(count.instructions.toArray()).filter(IincInsnNode.class::isInstance)
                        .map(IincInsnNode.class::cast).findFirst().orElseThrow().incr = 2;
                case "component" -> field(count, "ARMOR_SET").name = "CHARMS";
                case "group" -> field(equipment, "ARMOR").name = "HAND";
                case "type" -> field(equipment, "HUMANOID_ARMOR").name = "ANIMAL_ARMOR";
                case "slot" -> {
                    var read = Arrays.stream(equipment.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                            && call.name.equals("getItemBySlot")).findFirst().orElseThrow();
                    equipment.instructions.set(previous(read), new FieldInsnNode(GETSTATIC, AetherSetShape.SLOT, "HEAD", "L" + AetherSetShape.SLOT + ";"));
                }
                case "filter" -> {
                    var ret = last(equipment, ARETURN);
                    equipment.instructions.insertBefore(ret, new InsnNode(DUP));
                    equipment.instructions.insertBefore(ret, new MethodInsnNode(INVOKESTATIC, "fixture/Filter", "removeLegs", "(Ljava/util/List;)V", false));
                }
            }
            assertFalse(AetherSetShape.apply(node), fault);
            assertEquals(0, adapters(count), "Refusal must not leave a partial count hook: " + fault);
        }
    }

    @Test
    void tierDomainRefusesWrongSupplierAndUnknownReceiverWithoutPartialFluidEdits() throws Exception {
        for (String target : List.of(AetherAlkahestShape.FLUID, AetherAlkahestShape.EVENTS)) {
            for (String fault : List.of("supplier", "receiver")) {
                var node = nativeClass(target);
                var method = consumer(node);
                if (fault.equals("supplier")) {
                    field(method, "REINFORCEMENT_TIER").name = "CHARMS";
                } else {
                    var query = Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                            && call.owner.equals(AetherAlkahestShape.STACK) && call.name.equals("has")).findFirst().orElseThrow();
                    int supplier = method.maxLocals++;
                    method.instructions.insertBefore(query, new VarInsnNode(ASTORE, supplier));
                    method.instructions.insertBefore(query, new MethodInsnNode(INVOKESTATIC, "fixture/Receiver", "unknown",
                            "(L" + AetherAlkahestShape.STACK + ";)L" + AetherAlkahestShape.STACK + ";", false));
                    method.instructions.insertBefore(query, new VarInsnNode(ALOAD, supplier));
                }
                assertFalse(AetherAlkahestShape.apply(node), target + "/" + fault);
                assertEquals(0, adapters(method), "Domain proof must be atomic");
            }
        }
    }

    private static List<String> targets() {
        return List.of(AetherSetShape.EQUIPMENT, AetherAlkahestShape.FLUID, AetherAlkahestShape.EVENTS);
    }

    private static boolean apply(ClassNode node) throws Exception {
        return node.name.equals(AetherSetShape.EQUIPMENT) ? AetherSetShape.apply(node) : AetherAlkahestShape.apply(node);
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (var jar = new JarFile(System.getProperty("mismatchedleggings.test.aetherGuardJar"));
             var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode consumer(ClassNode node) {
        String name = node.name.equals(AetherSetShape.EQUIPMENT) ? "getArmorCount"
                : node.name.equals(AetherAlkahestShape.FLUID) ? "entityInside" : "onArmorDamaged";
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static FieldInsnNode field(MethodNode method, String name) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof FieldInsnNode field && field.name.equals(name))
                .map(FieldInsnNode.class::cast).findFirst().orElseThrow();
    }

    private static org.objectweb.asm.tree.AbstractInsnNode last(MethodNode method, int opcode) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() == opcode).reduce((a, b) -> b).orElseThrow();
    }

    private static org.objectweb.asm.tree.AbstractInsnNode previous(org.objectweb.asm.tree.AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static long adapters(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                && call.owner.startsWith("games/alejandrocoria/mismatchedleggings/compatibility/aether/")).count();
    }
}
