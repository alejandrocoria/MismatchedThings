package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearRecognitionShapeTest implements Opcodes {
    @Test void brilliantAllowsUnrelatedEditsButRejectsAnActorConsumerOrDifferentTrait() {
        var owner = brilliant();
        assertTrue(SilentGearRecognitionShape.brilliant(owner));
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelated", "()V", null, null));
        owner.methods.getFirst().instructions.insert(new InsnNode(NOP));
        assertTrue(SilentGearRecognitionShape.brilliant(owner));
        for (var node : owner.methods.getFirst().instructions) { if (node instanceof FieldInsnNode field) { field.name = "BOUNCE"; } }
        assertFalse(SilentGearRecognitionShape.brilliant(owner));
        owner = brilliant();
        owner.methods.getFirst().instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/ActorWriter", "changePlayer", "()V", false));
        assertFalse(SilentGearRecognitionShape.brilliant(owner));
    }

    @Test void moonSelectionDoesNotSelectBounceAndAmbiguousMoonProducersAreDenied() {
        var owner = fall(true, false);
        var method = owner.methods.getFirst();
        assertNotNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
        for (var node : method.instructions) { if (node instanceof FieldInsnNode field) { field.name = "BOUNCE"; } }
        assertNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
        owner = fall(true, false); method = owner.methods.getFirst();
        query(method);
        assertNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
    }

    @Test void moonProofRequiresItsOwnHookAndPreservesCompatibleResultChains() {
        assertFalse(SilentGearRecognitionShape.moonwalker(fall(false, false)));
        var owner = fall(true, true);
        owner.methods.getFirst().instructions.insert(new InsnNode(NOP));
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelated", "()V", null, null));
        assertTrue(SilentGearRecognitionShape.moonwalker(owner));
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgMoonLevel")) { call.name = "foreignReplacement"; }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.get(1).instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("moonwalker")) { call.name = "unverifiedRouting"; }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    @Test void moonRefusesChangedQueryOriginAndDifferentDistanceConsumer() {
        var owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("getHighestLevelArmorOrCurio")) { call.name = "unknownQuery"; }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("setDistance")) { call.owner = "foreign/OtherEvent"; }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    @Test void moonRefusesAResultStoredInADifferentLaneAndDuplicateOwnExtensions() {
        var owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof VarInsnNode var && var.getOpcode() == ISTORE) { var.var = 5; }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgMoonLevel")) {
                var second = new InsnList();
                second.add(new VarInsnNode(ALOAD, 0)); second.add(new MethodInsnNode(INVOKESTATIC, owner.name, call.name, call.desc, false));
                owner.methods.getFirst().instructions.insert(node, second); break;
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    private static ClassNode brilliant() {
        var owner = new ClassNode(); owner.name = SilentGearRecognitionShape.ARMOR;
        var method = new MethodNode(ACC_PUBLIC, "makesPiglinsNeutral", SilentGearRecognitionShape.BRILLIANT, null, null); owner.methods.add(method);
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new FieldInsnNode(GETSTATIC, SilentGearRecognitionShape.PREFIX + "util/Const$Traits", "BRILLIANT", SilentGearRecognitionShape.RESOURCE));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, SilentGearRecognitionShape.PREFIX + "util/TraitHelper", "hasTrait",
                "(Lnet/minecraft/world/item/ItemInstance;" + SilentGearRecognitionShape.RESOURCE + ")Z", false));
        method.instructions.add(new InsnNode(IRETURN)); return owner;
    }

    private static ClassNode fall(boolean own, boolean peer) {
        var owner = new ClassNode(); owner.name = SilentGearRecognitionShape.EVENTS;
        var method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "onLivingFall", SilentGearRecognitionShape.FALL, null, null); owner.methods.add(method);
        query(method);
        for (String name : own ? peer ? new String[]{"peer$query", "mismatchedboots$sgMoonLevel"} : new String[]{"mismatchedboots$sgMoonLevel"} : new String[]{}) {
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, name, SilentGearRecognitionShape.RESULT, false));
        }
        var code = method.instructions; var end = new LabelNode();
        code.add(new VarInsnNode(ISTORE, 3)); code.add(new VarInsnNode(ILOAD, 3)); code.add(new JumpInsnNode(IFLE, end));
        code.add(new InsnNode(FCONST_1)); code.add(new VarInsnNode(ILOAD, 3)); code.add(new InsnNode(I2F)); code.add(new LdcInsnNode(-0.15F));
        code.add(new InsnNode(FMUL)); code.add(new InsnNode(FADD)); code.add(new VarInsnNode(FSTORE, 4));
        code.add(new VarInsnNode(ALOAD, 0)); code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearRecognitionShape.EVENT, "getDistance", "()D", false));
        code.add(new VarInsnNode(FLOAD, 4)); code.add(new InsnNode(F2D)); code.add(new InsnNode(DMUL));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearRecognitionShape.EVENT, "setDistance", "(D)V", false));
        code.add(end); code.add(new InsnNode(RETURN));
        if (own) {
            var routing = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$sgMoonLevel", SilentGearRecognitionShape.RESULT, null, null);
            routing.instructions.add(new VarInsnNode(ILOAD, 0)); routing.instructions.add(new VarInsnNode(ALOAD, 1));
            routing.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedboots$sgMoonReady", "()Z", false));
            routing.instructions.add(new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearRecognition",
                    "moonwalker", "(IL" + SilentGearRecognitionShape.EVENT + ";Z)I", false));
            routing.instructions.add(new InsnNode(IRETURN)); owner.methods.add(routing);
        }
        return owner;
    }
    private static void query(MethodNode method) {
        method.instructions.add(new VarInsnNode(ALOAD, 2));
        method.instructions.add(new FieldInsnNode(GETSTATIC, SilentGearRecognitionShape.PREFIX + "util/Const$Traits", "MOONWALKER", SilentGearRecognitionShape.RESOURCE));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, SilentGearRecognitionShape.PREFIX + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY, false));
    }
}
