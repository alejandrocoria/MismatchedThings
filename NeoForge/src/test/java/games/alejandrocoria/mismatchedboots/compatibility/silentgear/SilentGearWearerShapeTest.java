package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Counterexamples protect the mod's actual paired injection contract, not generic ASM/Java behavior. */
class SilentGearWearerShapeTest implements Opcodes {
    @Test void selectsBothNativeSitesAndRejectsAnUnrelatedStackOrTraitProducer() {
        var method = counter(false, false);
        assertNotNull(SilentGearWearerShape.pair(method.desc, method.instructions));
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("trait")) { call.name = "otherTrait"; }
        }
        assertNull(SilentGearWearerShape.pair(method.desc, method.instructions));
        method = counter(false, false);
        for (var node : method.instructions) {
            if (node instanceof VarInsnNode var && var.getOpcode() == ASTORE && var.var == 7) { var.var = 8; }
        }
        assertNull(SilentGearWearerShape.pair(method.desc, method.instructions));
    }

    @Test void neitherHalfCanPublishPermissionAloneAndOrdinaryNativeLoopIsNotProof() {
        assertFalse(SilentGearWearerShape.set(owner(counter(false, false))));
        assertFalse(SilentGearWearerShape.set(owner(counter(true, false))));
        assertFalse(SilentGearWearerShape.set(owner(counter(false, true))));
        assertTrue(SilentGearWearerShape.set(owner(counter(true, true))));
    }

    @Test void acceptsCompatibleChainedResultsButNotChangedSlotLocalsOrConsumers() {
        var method = counter(true, true);
        method.instructions.insert(new InsnNode(NOP));
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgSetType")) {
                method.instructions.insert(node, handler("peer$headType")); break;
            }
        }
        assertTrue(SilentGearWearerShape.set(owner(method)));
        for (var node : method.instructions) {
            if (node instanceof VarInsnNode var && var.var == 6) { var.var = 8; break; }
        }
        assertFalse(SilentGearWearerShape.set(owner(method)));
        method = counter(true, true);
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("hasTrait")) { call.name = "alternateTraitConsumer"; }
        }
        assertFalse(SilentGearWearerShape.set(owner(method)));
    }

    @Test void refusesDifferentRejectTargetsAndChangedIncrementInsteadOfGuessingFinalCount() {
        var method = counter(true, true);
        for (var node : method.instructions) {
            if (node instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ) { jump.label = new LabelNode(); break; }
        }
        assertFalse(SilentGearWearerShape.set(owner(method)));
        method = counter(true, true);
        for (var node : method.instructions) { if (node instanceof IincInsnNode increment) { increment.incr = 2; } }
        assertFalse(SilentGearWearerShape.set(owner(method)));
    }

    @Test void refusesDuplicateOwnHooksAndRevokesExternallyPresetReadiness() {
        var method = counter(true, true);
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgSetType")) {
                method.instructions.insert(node, handler("mismatchedboots$sgSetType")); break;
            }
        }
        var owner = owner(method);
        assertFalse(SilentGearWearerShape.set(owner));
        owner = owner(counter(true, true));
        var getter = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$sgWielderReady", "()Z", null, null);
        getter.instructions.add(new InsnNode(ICONST_1)); getter.instructions.add(new InsnNode(IRETURN)); owner.methods.add(getter);
        SilentGearEffectShape.publish(owner, getter.name, SilentGearWearerShape.set(owner));
        assertTrue(getter.instructions.getFirst().getOpcode() == ICONST_0);
    }

    private static ClassNode owner(MethodNode method) {
        var owner = new ClassNode(); owner.name = SilentGearWearerShape.OWNER; owner.methods.add(method); return owner;
    }

    private static MethodNode counter(boolean typeHook, boolean traitHook) {
        var method = new MethodNode(ACC_PRIVATE, "getSetPieceCount", SilentGearWearerShape.COUNT, null, null);
        var code = method.instructions;
        var loop = new LabelNode(); var rejected = new LabelNode();
        code.add(loop); code.add(new VarInsnNode(ALOAD, 5));
        code.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)); code.add(new InsnNode(POP));
        code.add(new VarInsnNode(ALOAD, 5)); code.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
        code.add(new TypeInsnNode(CHECKCAST, SilentGearWearerShape.SLOT)); code.add(new VarInsnNode(ASTORE, 6));
        code.add(new VarInsnNode(ALOAD, 3)); code.add(new VarInsnNode(ALOAD, 6));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearWearerShape.PLAYER, "getItemBySlot", "(L" + SilentGearWearerShape.SLOT + ";)L" + SilentGearWearerShape.STACK + ";", false));
        code.add(new VarInsnNode(ASTORE, 7)); code.add(new VarInsnNode(ALOAD, 7));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearWearerShape.STACK, "getItem", "()Lnet/minecraft/world/item/Item;", false));
        code.add(new TypeInsnNode(INSTANCEOF, SilentGearWearerShape.ARMOR));
        if (typeHook) { code.add(handler("mismatchedboots$sgSetType")); }
        code.add(new JumpInsnNode(IFEQ, rejected)); code.add(new VarInsnNode(ALOAD, 7)); code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearWearerShape.CONTEXT, "trait", "()L" + SilentGearWearerShape.TRAIT + ";", false));
        code.add(new MethodInsnNode(INVOKESTATIC, SilentGearEffectShape.PREFIX + "util/TraitHelper", "hasTrait", SilentGearWearerShape.HAS, false));
        if (traitHook) { code.add(handler("mismatchedboots$sgSetTrait")); }
        code.add(new JumpInsnNode(IFEQ, rejected)); code.add(new IincInsnNode(4, 1));
        code.add(rejected); code.add(new JumpInsnNode(GOTO, loop)); return method;
    }

    private static InsnList handler(String name) {
        var code = new InsnList(); code.add(new VarInsnNode(ALOAD, 0)); code.add(new InsnNode(SWAP));
        for (int index : new int[]{1, 2, 3, 6, 7}) { code.add(new VarInsnNode(ALOAD, index)); }
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearWearerShape.OWNER, name,
                "(ZLjava/lang/Object;Ljava/lang/Object;L" + SilentGearWearerShape.PLAYER + ";L" + SilentGearWearerShape.SLOT + ";L" + SilentGearWearerShape.STACK + ";)Z", false));
        return code;
    }
}
