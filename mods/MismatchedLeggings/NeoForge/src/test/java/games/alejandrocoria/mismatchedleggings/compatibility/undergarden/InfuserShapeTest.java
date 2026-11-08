package games.alejandrocoria.mismatchedleggings.compatibility.undergarden;

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

import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real native bytecode and both independently packaged product transformations. */
class InfuserShapeTest implements Opcodes {
    @Test
    void independentProductHooksComposeInBothOrdersAndDoNotApplyTwice() throws Exception {
        for (String target : List.of(InfuserShape.RECIPE, InfuserShape.MENU)) {
            for (var order : List.of(List.of("boots", "leggings"), List.of("leggings", "boots"))) {
                var node = nativeClass(target);
                for (String product : order) {
                    assertTrue(apply(product, node), product + "/" + target);
                    assertFalse(apply(product, node), "Duplicate product contribution");
                }
                assertEquals(2, adapters(consumer(node)));
            }
        }
    }

    @Test
    void unrelatedInstructionsAndFinalBooleanPeerChainsRemainCompatible() throws Exception {
        for (String product : List.of("boots", "leggings")) {
            for (String target : List.of(InfuserShape.RECIPE, InfuserShape.MENU)) {
                var node = nativeClass(target);
                var method = consumer(node);
                method.instructions.insert(new InsnNode(NOP));
                var helper = new MethodNode(ACC_PRIVATE | ACC_STATIC, "peerBoolean", "(Z)Z", null, null);
                helper.instructions.add(new VarInsnNode(ILOAD, 0));
                helper.instructions.add(new InsnNode(IRETURN));
                helper.maxLocals = 1;
                helper.maxStack = 1;
                node.methods.add(helper);
                var consumer = Arrays.stream(method.instructions.toArray())
                        .filter(n -> n.getOpcode() == (target.equals(InfuserShape.RECIPE) ? IFEQ : IRETURN)).findFirst().orElseThrow();
                method.instructions.insertBefore(consumer, new MethodInsnNode(INVOKESTATIC, node.name, helper.name, helper.desc,
                        (node.access & ACC_INTERFACE) != 0));
                assertTrue(apply(product, node));
                assertEquals(1, adapters(method));
            }
        }
    }

    @Test
    void unknownRecipeMenuAndSlotOperandsRefuseWithoutPartialEdits() throws Exception {
        for (String product : List.of("boots", "leggings")) {
            for (String fault : List.of("ingredient", "recipeStack", "slot", "menuSet", "menuStack")) {
                var node = nativeClass(fault.startsWith("menu") ? InfuserShape.MENU : InfuserShape.RECIPE);
                var method = consumer(node);
                switch (fault) {
                    case "ingredient" -> call(method, "input").name = "unprovenInput";
                    case "slot" -> call(method, "type").name = "unprovenSlot";
                    case "menuSet" -> Arrays.stream(method.instructions.toArray()).filter(FieldInsnNode.class::isInstance)
                            .map(FieldInsnNode.class::cast).findFirst().orElseThrow().name = "otherInputs";
                    default -> {
                        var query = call(method, "test");
                        var change = new InsnList();
                        change.add(new MethodInsnNode(INVOKESTATIC, "fixture/Unknown", "stack",
                                "(L" + InfuserShape.STACK + ";)L" + InfuserShape.STACK + ";", false));
                        method.instructions.insertBefore(query, change);
                    }
                }
                assertFalse(apply(product, node), product + "/" + fault);
                assertEquals(0, adapters(method), "Refusal must be atomic");
            }
        }
    }

    private static boolean apply(String product, ClassNode node) throws Exception {
        if (product.equals("leggings")) {
            return InfuserShape.apply(node);
        }
        var type = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.undergarden.InfuserShape");
        var method = type.getDeclaredMethod("apply", ClassNode.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, node);
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (var jar = new JarFile(System.getProperty("mismatchedleggings.test.undergardenGuardJar"));
             var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode consumer(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals(node.name.equals(InfuserShape.RECIPE) ? "matches" : "canInfuse")
                && m.desc.equals(node.name.equals(InfuserShape.RECIPE)
                ? "(L" + InfuserShape.INPUT + ";Lnet/minecraft/world/level/Level;)Z" : "(L" + InfuserShape.STACK + ";)Z")).findFirst().orElseThrow();
    }

    private static MethodInsnNode call(MethodNode method, String name) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call && call.name.equals(name))
                .map(MethodInsnNode.class::cast).findFirst().orElseThrow();
    }

    private static long adapters(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode call
                && call.owner.endsWith("/undergarden/InfuserCompatibility")).count();
    }
}
