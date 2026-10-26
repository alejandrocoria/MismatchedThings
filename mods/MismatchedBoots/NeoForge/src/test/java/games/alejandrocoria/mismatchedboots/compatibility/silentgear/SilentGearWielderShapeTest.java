package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearWielderShapeTest implements Opcodes {
    @Test
    void wearerBoundaryRejectsAnUninspectedActorCallWithoutFingerprintingTheClass() {
        var owner = new ClassNode();
        owner.name = SilentGearWielderShape.OWNER;
        for (String name : new String[]{"inventoryTick", "lambda$inventoryTick$0", "applyEffects", "getSetPieceCount"}) {
            owner.methods.add(method(name));
        }
        owner.methods.add(method("unrelatedTooltip"));
        assertTrue(SilentGearWielderShape.effects(owner));
        owner.methods.get(2).instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Actor", "apply", "()V", false));
        assertFalse(SilentGearWielderShape.effects(owner));
    }

    @Test
    void gearIdentityMustStillRejectMixedItemsBeforeTheNativeTraitQuery() {
        var owner = new ClassNode();
        owner.name = SilentGearEffectShape.PREFIX + "util/GearHelper";
        var method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "isGear", "(Lnet/minecraft/world/item/ItemInstance;)Z", null, null);
        owner.methods.add(method);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE,
                                                   "net/minecraft/world/item/ItemInstance",
                                                   "typeHolder",
                                                   "()Lnet/minecraft/core/Holder;",
                                                   true));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "net/minecraft/core/Holder", "value", "()Ljava/lang/Object;", true));
        var check = new TypeInsnNode(INSTANCEOF, SilentGearEffectShape.PREFIX + "api/item/GearItem");
        method.instructions.add(check);
        method.instructions.add(new InsnNode(IRETURN));
        assertTrue(SilentGearWielderShape.identity(owner));
        check.desc = "net/minecraft/world/item/Item";
        assertFalse(SilentGearWielderShape.identity(owner));
    }

    @Test
    void potionBoundaryRejectsNewGearWritesAndUninspectedCallsButIgnoresUnrelatedMethods() {
        var owner = new ClassNode();
        owner.name = SilentGearWielderShape.DATA;
        var effect = method("getEffect");
        var level = method("getEffectLevel");
        owner.methods.add(effect);
        owner.methods.add(level);
        effect.instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, owner.name, "getEffectLevel", "(IIZ)I", false));
        owner.methods.add(method("unrelatedTooltip"));
        effect.instructions.insert(new InsnNode(NOP));
        assertTrue(SilentGearWielderShape.potion(owner));
        level.instructions.insert(new FieldInsnNode(PUTFIELD, "net/minecraft/world/item/ItemStack", "components", "Ljava/lang/Object;"));
        assertFalse(SilentGearWielderShape.potion(owner));
        level.instructions.clear();
        level.instructions.add(new MethodInsnNode(INVOKESTATIC, "foreign/Gear", "update", "()V", false));
        assertFalse(SilentGearWielderShape.potion(owner));
    }

    @Test
    void newlyReachableNonGearBranchMustReturnFalseBeforeAnyGearConsumer() {
        var owner = new ClassNode();
        owner.name = SilentGearWielderShape.QUERY;
        var method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "hasTrait", SilentGearWearerShape.HAS, null, null);
        owner.methods.add(method);
        var exit = new LabelNode();
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        var query = new MethodInsnNode(INVOKESTATIC,
                                       SilentGearEffectShape.PREFIX + "util/GearHelper",
                                       "isGear",
                                       "(Lnet/minecraft/world/item/ItemInstance;)Z",
                                       false);
        method.instructions.add(query);
        method.instructions.add(new JumpInsnNode(IFEQ, exit));
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new InsnNode(IRETURN));
        method.instructions.add(exit);
        method.instructions.add(new InsnNode(NOP));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(IRETURN));
        assertTrue(SilentGearWielderShape.query(owner));
        method.instructions.insert(exit, new MethodInsnNode(INVOKESTATIC, "foreign/Gear", "update", "()V", false));
        assertFalse(SilentGearWielderShape.query(owner));
        method.instructions.remove(exit.getNext());
        query.name = "unknownPredicate";
        assertFalse(SilentGearWielderShape.query(owner));
    }

    private static MethodNode method(String name) {
        var method = new MethodNode(ACC_PUBLIC, name, "()V", null, null);
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }
}
