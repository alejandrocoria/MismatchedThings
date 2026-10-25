package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearMagnetShapeTest implements Opcodes {
    private static final String QUEUE = SilentGearMagnetShape.TRACKER, VEC = SilentGearMagnetShape.VEC;

    @Test
    void admitsFiniteNativeDataWithoutFreezingDefaultsButRejectsUnboundedQueries() {
        assertTrue(SilentGearMagnetApi.parameters(.06F, 3F, 5));
        assertTrue(SilentGearMagnetApi.parameters(.12F, 2F, 3));
        assertFalse(SilentGearMagnetApi.parameters(Float.NaN, 3F, 5));
        assertFalse(SilentGearMagnetApi.parameters(.06F, Float.POSITIVE_INFINITY, 5));
        assertFalse(SilentGearMagnetApi.parameters(.06F, Float.MAX_VALUE, 5));
        assertFalse(SilentGearMagnetApi.parameters(.06F, 3F, 0));
        assertFalse(SilentGearMagnetApi.parameters(.06F, 3F, 128));
    }

    @Test
    void reachableGearWriteAndUninspectedDelegationAreNotReadOnly() {
        var owner = new ClassNode();
        var method = new MethodNode();
        method.instructions.add(new InsnNode(NOP));
        method.instructions.add(new InsnNode(RETURN));
        assertTrue(SilentGearMagnetShape.body(owner, method, Set.of(), Set.of(), null));
        method.instructions.insert(new FieldInsnNode(PUTFIELD, "gear", "damage", "I"));
        assertFalse(SilentGearMagnetShape.body(owner, method, Set.of(), Set.of(), null));
        method.instructions.clear();
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, "foreign", "tick", "()V", false));
        assertFalse(SilentGearMagnetShape.body(owner, method, Set.of(), Set.of(), null));
        method.instructions.clear();
        method.instructions.add(new InsnNode(AASTORE));
        assertFalse(SilentGearMagnetShape.body(owner, method, Set.of(), Set.of(), null));
    }

    @Test
    void queueWriteRequiresIncomingMagnitudeToWinAgainstPriorQueuedVector() {
        var method = arbitration(IFLE);
        assertTrue(SilentGearMagnetShape.strongest(method));
        method.instructions.insert(new InsnNode(NOP));
        assertTrue(SilentGearMagnetShape.strongest(method));
        assertFalse(SilentGearMagnetShape.strongest(arbitration(IFGE)));
        method.instructions.insertBefore(method.instructions.getLast(), new MethodInsnNode(INVOKEINTERFACE,
                                                                                           "java/util/Map",
                                                                                           "put",
                                                                                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                                                                           true));
        assertFalse(SilentGearMagnetShape.strongest(method));
    }

    @Test
    void finiteAdmissionRequiresTheProvedNativeRadiusProducer() {
        var method = new MethodNode();
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, SilentGearMagnetShape.EFFECT, "effectRange", "F"));
        method.instructions.add(new VarInsnNode(ILOAD, 2));
        method.instructions.add(new InsnNode(I2F));
        method.instructions.add(new InsnNode(FMUL));
        method.instructions.add(new InsnNode(FCONST_1));
        var add = new InsnNode(FADD);
        method.instructions.add(add);
        method.instructions.add(new VarInsnNode(FSTORE, 3));
        assertTrue(SilentGearMagnetShape.radius(method));
        method.instructions.insert(new InsnNode(NOP));
        assertTrue(SilentGearMagnetShape.radius(method));
        method.instructions.set(add, new InsnNode(FSUB));
        assertFalse(SilentGearMagnetShape.radius(method));
    }

    @Test
    void queueMustApplyBeforeClearingAndLambdaCannotRedirectPublication() {
        var owner = new ClassNode();
        owner.name = QUEUE;
        var method = new MethodNode();
        var lambda = new MethodNode(ACC_STATIC, "anyGeneratedName", SilentGearMagnetShape.PUSH, null, null);
        lambda.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, SilentGearMagnetShape.ITEM, "push", "(DDD)V", false));
        owner.methods.add(lambda);
        method.instructions.add(new FieldInsnNode(GETSTATIC, QUEUE, "ITEMS_TO_PUSH", "Ljava/util/Map;"));
        method.instructions.add(new InvokeDynamicInsnNode("accept",
                                                          "()Ljava/util/function/BiConsumer;",
                                                          new Handle(H_INVOKESTATIC,
                                                                     "java/lang/invoke/LambdaMetafactory",
                                                                     "metafactory",
                                                                     SilentGearMagnetShape.META,
                                                                     false),
                                                          org.objectweb.asm.Type.getMethodType("(Ljava/lang/Object;Ljava/lang/Object;)V"),
                                                          new Handle(H_INVOKESTATIC, QUEUE, lambda.name, lambda.desc, false),
                                                          org.objectweb.asm.Type.getMethodType(lambda.desc)));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "forEach", "(Ljava/util/function/BiConsumer;)V", true));
        method.instructions.add(new FieldInsnNode(GETSTATIC, QUEUE, "ITEMS_TO_PUSH", "Ljava/util/Map;"));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "clear", "()V", true));
        assertTrue(SilentGearMagnetShape.drains(method));
        Set<String> fields = Set.of(QUEUE + ".ITEMS_TO_PUSH:Ljava/util/Map;");
        Set<String> calls = Set.of("java/util/Map.forEach(Ljava/util/function/BiConsumer;)V", "java/util/Map.clear()V");
        assertTrue(SilentGearMagnetShape.body(owner, method, calls, fields, lambda.desc));
        lambda.instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign", "moveGear", "()V", false));
        assertFalse(SilentGearMagnetShape.body(owner, method, calls, fields, lambda.desc));
        method.instructions.insert(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "clear", "()V", true));
        assertFalse(SilentGearMagnetShape.drains(method));
    }

    private static MethodNode arbitration(int branch) {
        var method = new MethodNode();
        var skip = new LabelNode();
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE,
                                                   "java/util/Map",
                                                   "getOrDefault",
                                                   "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                                   true));
        method.instructions.add(new TypeInsnNode(CHECKCAST, VEC));
        method.instructions.add(new VarInsnNode(ASTORE, 3));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, VEC, "lengthSqr", "()D", false));
        method.instructions.add(new VarInsnNode(ALOAD, 3));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, VEC, "lengthSqr", "()D", false));
        method.instructions.add(new InsnNode(DCMPL));
        method.instructions.add(new JumpInsnNode(branch, skip));
        method.instructions.add(new FieldInsnNode(GETSTATIC, QUEUE, "ITEMS_TO_PUSH", "Ljava/util/Map;"));
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new MethodInsnNode(INVOKEINTERFACE,
                                                   "java/util/Map",
                                                   "put",
                                                   "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                                   true));
        method.instructions.add(skip);
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }
}
