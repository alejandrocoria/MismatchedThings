package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysticalRefundShapeTest implements Opcodes {
    @Test
    void nativeBoundaryDoesNotAuthorizeWithoutOurTransformedObserver() throws Exception {
        var node = nativeStack();
        var shape = shape(node);
        assertTrue(shape.entry(node));
        assertFalse(shape.publish(node));
        node.methods.removeIf(m -> m.name.equals("inventoryTick"));
        assertTrue(shape.entry(node), "Unrelated item behavior is not part of the consumption proof");
    }

    @Test
    void countBrokenConditionDamageReceiverAndOwnerSignatureAreRequired() throws Exception {
        for (String fault : new String[]{"count", "branch", "damage", "signature"}) {
            var node = nativeStack();
            var shape = shape(node);
            var apply = node.methods.stream().filter(m -> m.name.equals("applyDamage") && m.desc.equals(shape.APPLY)).findFirst().orElseThrow();
            int changed = 0;
            if (fault.equals("signature")) {
                apply.desc = "(ILjava/util/function/Consumer;)V";
                changed++;
            } else {
                for (var instruction : apply.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode call)) {
                        continue;
                    }
                    if (fault.equals("count") && call.name.equals("shrink")) {
                        apply.instructions.set(call.getPrevious(), new InsnNode(ICONST_2));
                        changed++;
                    } else if (fault.equals("branch") && call.name.equals("isBroken")) {
                        call.name = "isEmpty";
                        changed++;
                    } else if (fault.equals("damage") && call.name.equals("setDamageValue")) {
                        apply.instructions.set(call.getPrevious(), new InsnNode(ICONST_0));
                        changed++;
                    }
                }
            }

            assertEquals(1, changed, fault);
            assertFalse(shape.entry(node), fault);
        }
    }

    private static MysticalRefundShape shape(ClassNode node) {
        return new MysticalRefundShape(node.methods.stream().noneMatch(m -> m.name.equals("applyDamage")
                && m.desc.contains("/LivingEntity;")));
    }

    private static ClassNode nativeStack() throws Exception {
        var node = new ClassNode();
        try (var stream = MysticalRefundShapeTest.class.getClassLoader().getResourceAsStream(MysticalRefundShape.OWNER + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
}
