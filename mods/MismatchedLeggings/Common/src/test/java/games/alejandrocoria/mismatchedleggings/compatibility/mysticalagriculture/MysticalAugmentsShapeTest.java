package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysticalAugmentsShapeTest implements Opcodes {
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String LIST = "Ljava/util/List;";

    @Test void equipmentAcceptsSiblingForwardingButRejectsOpaqueSlotOrDuplicateBridge() {
        var node = equipment();
        assertTrue(MysticalAugmentsShape.equipment(node));
        // The independent Boots view is reachable, but does not count as our bridge.
        node.methods.getLast().instructions.add(new MethodInsnNode(INVOKESTATIC,
                "games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalAugmentsCompatibility",
                "equipment", "(" + PLAYER + STACK + LIST + ")" + LIST, false));
        node.methods.getLast().instructions.insert(new InsnNode(NOP));
        assertTrue(MysticalAugmentsShape.equipment(node));
        var read = (MethodInsnNode) node.methods.getFirst().instructions.getFirst();
        read.name = "opaqueSlot";
        assertFalse(MysticalAugmentsShape.equipment(node));
        node = equipment();
        node.methods.getLast().instructions.add(bridge());
        assertFalse(MysticalAugmentsShape.equipment(node));
    }

    @Test void armorRefusesNewStackDependencyIndependentlyOfOtherBodies() {
        var node = new ClassNode();
        var tick = new MethodNode(ACC_PUBLIC, "onArmorTick", "(" + STACK + "Lnet/minecraft/server/level/ServerLevel;" + PLAYER + ")V", null, null);
        node.methods.add(tick);
        tick.instructions.add(new VarInsnNode(ALOAD, 3));
        assertTrue(MysticalAugmentsShape.armorTick(node));
        tick.instructions.add(new VarInsnNode(ALOAD, 1));
        assertFalse(MysticalAugmentsShape.armorTick(node));
    }

    private static ClassNode equipment() {
        var node = new ClassNode(); node.name = MysticalAugmentsShape.UTILS;
        var read = new MethodNode(ACC_STATIC, "getArmorAugments", "(" + PLAYER + ")" + LIST, null, null);
        read.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)" + STACK, false));
        read.instructions.add(new MethodInsnNode(INVOKESTATIC, node.name, "peerForward", "()V", false));
        var forward = new MethodNode(ACC_STATIC, "peerForward", "()V", null, null);
        forward.instructions.add(new MethodInsnNode(INVOKESTATIC, node.name, "getAugments", "(" + STACK + ")" + LIST, false));
        forward.instructions.add(bridge());
        node.methods.add(read); node.methods.add(forward);
        return node;
    }
    private static MethodInsnNode bridge() {
        return new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedleggings/compatibility/mysticalagriculture/MysticalAugmentsCompatibility", "equipment", "(" + PLAYER + STACK + LIST + ")" + LIST, false);
    }
}
