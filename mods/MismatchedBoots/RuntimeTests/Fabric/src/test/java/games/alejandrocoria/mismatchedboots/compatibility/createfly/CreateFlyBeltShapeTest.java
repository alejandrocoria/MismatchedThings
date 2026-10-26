package games.alejandrocoria.mismatchedboots.compatibility.createfly;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateFlyBeltShapeTest {
    @Test
    void selectsOnlyCardboardExitEvenWithOtherStackPredicatesAndEquipmentReads() {
        MethodNode method = fixture();
        var selected = CreateFlyBeltShape.cardboardChecks(method.instructions).getFirst();
        method.instructions.insertBefore(method.instructions.getFirst(), unrelatedPredicate());
        assertEquals(java.util.List.of(selected), CreateFlyBeltShape.cardboardChecks(method.instructions));
        assertTrue(CreateFlyMixinPlugin.hasCompatibleBeltConsumer(belt(method)));
    }

    @Test
    void missingAmbiguousOrChangedLocalContractFailsClosed() {
        MethodNode method = fixture();
        method.instructions.add(fixture().instructions);
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
        assertFalse(CreateFlyMixinPlugin.hasCompatibleBeltConsumer(belt(method)));
        method = fixture();
        ((VarInsnNode) method.instructions.get(3)).var = 8; // Different player for shift and boots.
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
        method = fixture();
        ((FieldInsnNode) method.instructions.get(4)).name = "HEAD";
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
        method = fixture();
        ((FieldInsnNode) method.instructions.get(6)).name = "COPPER_DIVING_BOOTS";
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
        method = fixture();
        ((JumpInsnNode) method.instructions.get(8)).setOpcode(Opcodes.IFEQ);
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
        method = fixture();
        method.instructions.set(method.instructions.get(9), new InsnNode(Opcodes.NOP));
        assertTrue(CreateFlyBeltShape.cardboardChecks(method.instructions).isEmpty());
    }

    @Test
    void metadataAndLocalRelocationDoNotDependOnGlobalMethodCounts() {
        MethodNode method = fixture();
        LabelNode label = new LabelNode();
        method.instructions.insert(label);
        method.instructions.insert(label, new LineNumberNode(42, label));
        method.instructions.insert(new InsnNode(Opcodes.NOP));
        for (var node : method.instructions) {
            if (node instanceof VarInsnNode local && local.var == 7) {
                local.var = 12;
            }
        }
        assertTrue(CreateFlyMixinPlugin.hasCompatibleBeltConsumer(belt(method)));
        method.access |= Opcodes.ACC_STATIC;
        assertFalse(CreateFlyMixinPlugin.hasCompatibleBeltConsumer(belt(method)));
    }

    private static ClassNode belt(MethodNode method) {
        ClassNode node = new ClassNode();
        node.access = Opcodes.ACC_PUBLIC;
        node.methods.add(method);
        return node;
    }

    private static InsnList unrelatedPredicate() {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 7));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                                   "Lnet/minecraft/world/entity/EquipmentSlot;"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player",
                                    "getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/item/Items", "DIAMOND",
                                   "Lnet/minecraft/world/item/Item;"));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "is",
                                    "(Ljava/lang/Object;)Z", false));
        code.add(new InsnNode(Opcodes.POP));
        return code;
    }

    private static MethodNode fixture() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "entityInside", CreateFlyBeltShape.DESCRIPTOR, null, null);
        LabelNode accepted = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player",
                                                   "isShiftKeyDown", "()Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, accepted));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                                                  "Lnet/minecraft/world/entity/EquipmentSlot;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                                   "net/minecraft/world/entity/player/Player",
                                                   "getItemBySlot",
                                                   "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
                                                   false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/zurrtum/create/AllItems", "CARDBOARD_BOOTS",
                                                  "Lcom/zurrtum/create/content/equipment/armor/CardboardArmorItem;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "is",
                                                   "(Ljava/lang/Object;)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, accepted));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(accepted);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }
}
