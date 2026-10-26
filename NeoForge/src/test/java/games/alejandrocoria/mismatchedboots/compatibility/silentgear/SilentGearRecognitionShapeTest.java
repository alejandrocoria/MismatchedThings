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
    @Test
    void brilliantAllowsUnrelatedEditsButRejectsAnActorConsumerOrDifferentTrait() {
        var owner = brilliant();
        assertTrue(SilentGearRecognitionShape.brilliant(owner));
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelated", "()V", null, null));
        owner.methods.getFirst().instructions.insert(new InsnNode(NOP));
        assertTrue(SilentGearRecognitionShape.brilliant(owner));
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof FieldInsnNode field) {
                field.name = "BOUNCE";
            }
        }
        assertFalse(SilentGearRecognitionShape.brilliant(owner));
        owner = brilliant();
        owner.methods.getFirst().instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/ActorWriter", "changePlayer", "()V", false));
        assertFalse(SilentGearRecognitionShape.brilliant(owner));
    }

    @Test
    void moonSelectionDoesNotSelectBounceAndAmbiguousMoonProducersAreDenied() {
        var owner = fall(true, false);
        var method = owner.methods.getFirst();
        assertNotNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field) {
                field.name = "BOUNCE";
            }
        }
        assertNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
        owner = fall(true, false);
        method = owner.methods.getFirst();
        query(method);
        assertNull(SilentGearRecognitionShape.moonQuery(method.desc, method.instructions));
    }

    @Test
    void moonProofRequiresItsOwnHookAndPreservesCompatibleResultChains() {
        assertFalse(SilentGearRecognitionShape.moonwalker(fall(false, false)));
        var owner = fall(true, true);
        owner.methods.getFirst().instructions.insert(new InsnNode(NOP));
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelated", "()V", null, null));
        assertTrue(SilentGearRecognitionShape.moonwalker(owner));
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgMoonLevel")) {
                call.name = "foreignReplacement";
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.get(1).instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("moonwalker")) {
                call.name = "unverifiedRouting";
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    @Test
    void moonRefusesChangedQueryOriginAndDifferentDistanceConsumer() {
        var owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("getHighestLevelArmorOrCurio")) {
                call.name = "unknownQuery";
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("setDistance")) {
                call.owner = "foreign/OtherEvent";
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    @Test
    void moonRefusesAResultStoredInADifferentLaneAndDuplicateOwnExtensions() {
        var owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof VarInsnNode var && var.getOpcode() == ISTORE) {
                var.var = 5;
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
        owner = fall(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof MethodInsnNode call && call.name.contains("sgMoonLevel")) {
                var second = new InsnList();
                second.add(new VarInsnNode(ALOAD, 0));
                second.add(new MethodInsnNode(INVOKESTATIC, owner.name, call.name, call.desc, false));
                owner.methods.getFirst().instructions.insert(node, second);
                break;
            }
        }
        assertFalse(SilentGearRecognitionShape.moonwalker(owner));
    }

    @Test
    void voidSelectionRejectsAnotherTraitPlayerOriginAndAmbiguousQueries() {
        var owner = tick(true, false);
        var method = owner.methods.getFirst();
        assertNotNull(SilentGearRecognitionShape.voidQuery(method.desc, method.instructions));
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field) {
                field.name = "BRILLIANT";
            }
        }
        assertNull(SilentGearRecognitionShape.voidQuery(method.desc, method.instructions));
        owner = tick(true, false);
        method = owner.methods.getFirst();
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("getEntity")) {
                call.name = "anotherPlayer";
            }
        }
        assertNull(SilentGearRecognitionShape.voidQuery(method.desc, method.instructions));
        owner = tick(true, false);
        method = owner.methods.getFirst();
        voidQuery(method, "VOID_WARD");
        assertNull(SilentGearRecognitionShape.voidQuery(method.desc, method.instructions));
    }

    @Test
    void voidProofAcceptsCompatibleHookChainsAndIgnoresUnrelatedQueries() {
        assertFalse(SilentGearRecognitionShape.voidWard(tick(false, false)));
        var owner = tick(true, true);
        var method = owner.methods.getFirst();
        method.instructions.insert(new InsnNode(NOP));
        // An extra unrelated lookup does not make the specifically selected query ambiguous.
        var extra = new MethodNode();
        voidQuery(extra, "BRILLIANT");
        extra.instructions.add(new InsnNode(POP));
        method.instructions.insertBefore(SilentGearRecognitionShape.voidQuery(method.desc, method.instructions).getPrevious().getPrevious(),
                                         extra.instructions);
        owner.methods.add(new MethodNode(ACC_PUBLIC, "unrelated", "()V", null, null));
        assertTrue(SilentGearRecognitionShape.voidWard(owner));
    }

    @Test
    void voidProofRejectsReplacedRoutingAndDiscardedQueryResults() {
        var owner = tick(true, false);
        for (var node : owner.methods.get(1).instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("voidWard")) {
                call.name = "unprovedRouting";
            }
        }
        assertFalse(SilentGearRecognitionShape.voidWard(owner));
        owner = tick(true, false);
        for (var node : owner.methods.getFirst().instructions) {
            if (node instanceof JumpInsnNode) {
                owner.methods.getFirst().instructions.set(node, new InsnNode(POP));
                break;
            }
        }
        assertFalse(SilentGearRecognitionShape.voidWard(owner));
    }

    private static ClassNode tick(boolean own, boolean peer) {
        var owner = new ClassNode();
        owner.name = SilentGearRecognitionShape.EVENTS;
        var method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "onPlayerTick", SilentGearRecognitionShape.TICK, null, null);
        owner.methods.add(method);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                                                   SilentGearRecognitionShape.PLAYER_PRE,
                                                   "getEntity",
                                                   "()Lnet/minecraft/world/entity/player/Player;",
                                                   false));
        method.instructions.add(new VarInsnNode(ASTORE, 1));
        voidQuery(method, "VOID_WARD");
        for (String name : own
                           ? peer ? new String[]{"peer$query", "mismatchedboots$sgVoidWard"} : new String[]{"mismatchedboots$sgVoidWard"}
                           : new String[]{}) {
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, name, SilentGearRecognitionShape.VOID_RESULT, false));
        }

        var end = new LabelNode();
        method.instructions.add(new JumpInsnNode(IFEQ, end));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(RETURN));
        if (own) {
            var route = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$sgVoidWard", SilentGearRecognitionShape.VOID_RESULT, null, null);
            route.instructions.add(new VarInsnNode(ILOAD, 0));
            route.instructions.add(new VarInsnNode(ALOAD, 1));
            route.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedboots$sgVoidReady", "()Z", false));
            route.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                      "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearRecognition",
                                                      "voidWard",
                                                      "(ZL" + SilentGearRecognitionShape.PLAYER_PRE + ";Z)Z",
                                                      false));
            route.instructions.add(new InsnNode(IRETURN));
            owner.methods.add(route);
        }

        return owner;
    }

    private static void voidQuery(MethodNode method, String trait) {
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new FieldInsnNode(GETSTATIC,
                                                  SilentGearRecognitionShape.PREFIX + "util/Const$Traits",
                                                  trait,
                                                  SilentGearRecognitionShape.RESOURCE));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                   SilentGearRecognitionShape.PREFIX + "util/TraitHelper",
                                                   "hasTraitArmor",
                                                   SilentGearRecognitionShape.VOID_QUERY,
                                                   false));
    }

    private static ClassNode brilliant() {
        var owner = new ClassNode();
        owner.name = SilentGearRecognitionShape.ARMOR;
        var method = new MethodNode(ACC_PUBLIC, "makesPiglinsNeutral", SilentGearRecognitionShape.BRILLIANT, null, null);
        owner.methods.add(method);
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new FieldInsnNode(GETSTATIC,
                                                  SilentGearRecognitionShape.PREFIX + "util/Const$Traits",
                                                  "BRILLIANT",
                                                  SilentGearRecognitionShape.RESOURCE));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, SilentGearRecognitionShape.PREFIX + "util/TraitHelper", "hasTrait",
                                                   "(Lnet/minecraft/world/item/ItemInstance;" + SilentGearRecognitionShape.RESOURCE + ")Z", false));
        method.instructions.add(new InsnNode(IRETURN));
        return owner;
    }

    private static ClassNode fall(boolean own, boolean peer) {
        var owner = new ClassNode();
        owner.name = SilentGearRecognitionShape.EVENTS;
        var method = new MethodNode(ACC_PUBLIC | ACC_STATIC, "onLivingFall", SilentGearRecognitionShape.FALL, null, null);
        owner.methods.add(method);
        query(method);
        for (String name : own
                           ? peer ? new String[]{"peer$query", "mismatchedboots$sgMoonLevel"} : new String[]{"mismatchedboots$sgMoonLevel"}
                           : new String[]{}) {
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, name, SilentGearRecognitionShape.RESULT, false));
        }

        var code = method.instructions;
        var end = new LabelNode();
        code.add(new VarInsnNode(ISTORE, 3));
        code.add(new VarInsnNode(ILOAD, 3));
        code.add(new JumpInsnNode(IFLE, end));
        code.add(new InsnNode(FCONST_1));
        code.add(new VarInsnNode(ILOAD, 3));
        code.add(new InsnNode(I2F));
        code.add(new LdcInsnNode(-0.15F));
        code.add(new InsnNode(FMUL));
        code.add(new InsnNode(FADD));
        code.add(new VarInsnNode(FSTORE, 4));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearRecognitionShape.EVENT, "getDistance", "()D", false));
        code.add(new VarInsnNode(FLOAD, 4));
        code.add(new InsnNode(F2D));
        code.add(new InsnNode(DMUL));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearRecognitionShape.EVENT, "setDistance", "(D)V", false));
        code.add(end);
        code.add(new InsnNode(RETURN));
        if (own) {
            var routing = new MethodNode(ACC_PRIVATE | ACC_STATIC, "mismatchedboots$sgMoonLevel", SilentGearRecognitionShape.RESULT, null, null);
            routing.instructions.add(new VarInsnNode(ILOAD, 0));
            routing.instructions.add(new VarInsnNode(ALOAD, 1));
            routing.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedboots$sgMoonReady", "()Z", false));
            routing.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                        "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearRecognition",
                                                        "moonwalker",
                                                        "(IL" + SilentGearRecognitionShape.EVENT + ";Z)I",
                                                        false));
            routing.instructions.add(new InsnNode(IRETURN));
            owner.methods.add(routing);
        }

        return owner;
    }

    private static void query(MethodNode method) {
        method.instructions.add(new VarInsnNode(ALOAD, 2));
        method.instructions.add(new FieldInsnNode(GETSTATIC,
                                                  SilentGearRecognitionShape.PREFIX + "util/Const$Traits",
                                                  "MOONWALKER",
                                                  SilentGearRecognitionShape.RESOURCE));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                   SilentGearRecognitionShape.PREFIX + "util/TraitHelper",
                                                   "getHighestLevelArmorOrCurio",
                                                   SilentGearRecognitionShape.QUERY,
                                                   false));
    }
}
