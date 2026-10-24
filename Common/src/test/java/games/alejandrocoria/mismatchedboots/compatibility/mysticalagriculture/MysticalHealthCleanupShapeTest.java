package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysticalHealthCleanupShapeTest implements Opcodes {
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";

    @Test void acceptsLocalForwardingAndUnrelatedNativeBodyChanges() {
        var node = fixture();
        node.methods.getFirst().instructions.insert(new InsnNode(NOP));
        assertTrue(MysticalAugmentsShape.healthCleanup(node));
    }

    @Test void refusesMissingWrapperWithoutFingerprintingOtherFamilies() {
        var node = fixture();
        node.methods.getLast().instructions.remove(node.methods.getLast().instructions.getFirst());
        assertFalse(MysticalAugmentsShape.healthCleanup(node));
    }

    @Test void refusesAmbiguousRegistrationOrUnreachableBridge() {
        var node = fixture();
        node.methods.getLast().instructions.add(registration());
        assertFalse(MysticalAugmentsShape.healthCleanup(node));
        node = fixture();
        node.methods.getFirst().instructions.clear();
        node.methods.getFirst().instructions.add(registration());
        assertFalse(MysticalAugmentsShape.healthCleanup(node));
    }

    private static ClassNode fixture() {
        var node = new ClassNode(); node.name = "com/blakebr0/mysticalagriculture/augment/HealthBoostAugment";
        var tick = new MethodNode(ACC_PUBLIC, "onPlayerTick", "(Lnet/minecraft/world/level/Level;" + PLAYER
                + "Lcom/blakebr0/mysticalagriculture/api/lib/AbilityCache;)V", null, null);
        tick.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "forward", "()V", false));
        var forward = new MethodNode(ACC_PRIVATE, "forward", "()V", null, null);
        forward.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalAugmentsCompatibility",
                "refreshHealthCleanup", "(Ljava/lang/Object;Ljava/lang/Object;" + PLAYER + ")V", false));
        forward.instructions.add(registration());
        node.methods.add(tick); node.methods.add(forward); return node;
    }

    private static MethodInsnNode registration() {
        return new MethodInsnNode(INVOKEVIRTUAL, "com/blakebr0/mysticalagriculture/api/lib/AbilityCache", "add",
                "(Lcom/blakebr0/mysticalagriculture/api/tinkering/Augment;" + PLAYER + "Ljava/lang/Runnable;)V", false);
    }
}
