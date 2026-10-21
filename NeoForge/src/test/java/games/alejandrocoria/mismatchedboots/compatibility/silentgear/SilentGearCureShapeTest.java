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

class SilentGearCureShapeTest implements Opcodes {
    @Test void admitsSelectedContextActorLoopWithRenumberedLocalsAndUnrelatedEdits() {
        var node = fixture();
        node.methods.getFirst().instructions.insert(new InsnNode(NOP));
        for (var insn : node.methods.getFirst().instructions) {
            if (insn instanceof VarInsnNode variable && variable.var >= 5) { variable.var += 4; }
        }
        node.methods.add(new MethodNode(ACC_PUBLIC, "unrelatedPresentation", "()V", null, null));
        assertTrue(SilentGearCureShape.admitted(node));
    }

    @Test void refusesChangedSelectionActorTargetOrConsumer() {
        for (String fault : new String[]{"selected", "actor", "targets", "consumer", "exit", "loop"}) {
            var node = fixture(); var code = node.methods.getFirst().instructions;
            switch (fault) {
                case "selected" -> ((VarInsnNode) code.getFirst()).var = 2;
                case "actor" -> { for (var insn : code) { if (insn instanceof MethodInsnNode call && call.name.equals("player")) { call.name = "otherActor"; } } }
                case "targets" -> { for (var insn : code) { if (insn instanceof FieldInsnNode field) { field.name = "otherEffects"; } } }
                case "consumer" -> { for (var insn : code) { if (insn instanceof MethodInsnNode call && call.name.equals("removeEffect")) { call.name = "unknownWriter"; } } }
                case "exit" -> ((JumpInsnNode) code.get(1)).setOpcode(IFNE);
                case "loop" -> { for (var insn : code) { if (insn instanceof JumpInsnNode jump && jump.getOpcode() == GOTO) { jump.label = ((JumpInsnNode) code.get(1)).label; } } }
                default -> throw new AssertionError(fault);
            }
            assertFalse(SilentGearCureShape.admitted(node), fault);
        }
    }

    @Test void refusesAdditionalGearOrActorWorkRatherThanCallingAnUnauditedTick() {
        var node = fixture();
        node.methods.getFirst().instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/GearWriter", "recalculate", "()V", false));
        assertFalse(SilentGearCureShape.admitted(node));
        node = fixture(); node.methods.getFirst().instructions.clear(); node.methods.getFirst().instructions.add(new InsnNode(RETURN));
        assertFalse(SilentGearCureShape.admitted(node));
    }

    private static ClassNode fixture() {
        var node = new ClassNode(); node.name = SilentGearCureShape.OWNER;
        var method = new MethodNode(ACC_PUBLIC, "inventoryTick", SilentGearEffectShape.TICK, null, null); node.methods.add(method);
        var code = method.instructions; var end = new LabelNode(); var loop = new LabelNode();
        code.add(new VarInsnNode(ILOAD, 4)); code.add(new JumpInsnNode(IFEQ, end));
        code.add(new VarInsnNode(ALOAD, 1)); code.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearEffectShape.CONTEXT, "player", "()Lnet/minecraft/world/entity/player/Player;", false));
        code.add(new VarInsnNode(ASTORE, 5)); code.add(new VarInsnNode(ALOAD, 5)); code.add(new JumpInsnNode(IFNULL, end));
        code.add(new VarInsnNode(ALOAD, 0)); code.add(new FieldInsnNode(GETFIELD, node.name, "effects", "Ljava/util/List;"));
        code.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true));
        code.add(new VarInsnNode(ASTORE, 6)); code.add(loop); code.add(new VarInsnNode(ALOAD, 6));
        code.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)); code.add(new JumpInsnNode(IFEQ, end));
        code.add(new VarInsnNode(ALOAD, 6)); code.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
        code.add(new TypeInsnNode(CHECKCAST, "net/minecraft/core/Holder")); code.add(new VarInsnNode(ASTORE, 7));
        code.add(new VarInsnNode(ALOAD, 5)); code.add(new VarInsnNode(ALOAD, 7));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "removeEffect", "(Lnet/minecraft/core/Holder;)Z", false));
        code.add(new InsnNode(POP)); code.add(new JumpInsnNode(GOTO, loop)); code.add(end); code.add(new InsnNode(RETURN));
        return node;
    }
}
