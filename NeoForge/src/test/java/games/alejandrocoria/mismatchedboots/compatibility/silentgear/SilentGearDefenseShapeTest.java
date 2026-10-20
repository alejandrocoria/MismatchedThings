package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearDefenseShapeTest implements Opcodes {
    @Test void bounceSelectionRequiresTheChosenIdAndTheCorrectArmorQueryScope() {
        String p = SilentGearDefenseShape.P, ref = SilentGearRecognitionShape.RESOURCE;
        var c = new InsnList(); c.add(new VarInsnNode(ALOAD, 8)); c.add(new FieldInsnNode(GETSTATIC, p + "util/Const$Traits", "MOONWALKER", ref));
        c.add(new MethodInsnNode(INVOKESTATIC, p + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY, false)); c.add(new InsnNode(POP));
        c.add(new VarInsnNode(ALOAD, 4)); var id = new FieldInsnNode(GETSTATIC, p + "util/Const$Traits", "BOUNCE", ref); c.add(id);
        var query = new MethodInsnNode(INVOKESTATIC, p + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY, false); c.add(query);
        assertSame(query, SilentGearDefenseShape.bounceQuery(c, true)); assertNull(SilentGearDefenseShape.bounceQuery(c, false));
        query.name = "getHighestLevelArmor"; assertSame(query, SilentGearDefenseShape.bounceQuery(c, false));
        assertNull(SilentGearDefenseShape.bounceQuery(c, true)); id.name = "FOREIGN"; assertNull(SilentGearDefenseShape.bounceQuery(c, false));
    }

    @Test void levelRoutingRequiresItsOwnIndependentPermissionAndReadOnlyAdapter() {
        var owner = new ClassNode(); owner.name = SilentGearDefenseShape.EVENTS;
        var m = new MethodNode(ACC_PRIVATE | ACC_STATIC, "ownedLevel", "(IL" + SilentGearDefenseShape.EVENT + ";)I", null, null); owner.methods.add(m);
        m.instructions.add(new VarInsnNode(ILOAD, 0)); m.instructions.add(new VarInsnNode(ALOAD, 1));
        var proof = new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedboots$sgBounceFallReady", "()Z", false); m.instructions.add(proof);
        var route = new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearDefense", "fallLevel", "(IL" + SilentGearDefenseShape.EVENT + ";Z)I", false); m.instructions.add(route);
        m.instructions.add(new InsnNode(IRETURN)); var site = new MethodInsnNode(INVOKESTATIC, owner.name, m.name, m.desc, false);
        assertTrue(SilentGearDefenseShape.levelRouting(owner, site, true));
        proof.name = "mismatchedboots$sgSafeFallReady"; assertFalse(SilentGearDefenseShape.levelRouting(owner, site, true));
        proof.name = "mismatchedboots$sgBounceFallReady"; route.name = "unprovedAlias"; assertFalse(SilentGearDefenseShape.levelRouting(owner, site, true));
    }

    @Test void incomingLambdaRequiresNativeCurrentAndCommonBaseLanes() {
        var owner = new ClassNode(); owner.name = SilentGearDefenseShape.EVENTS;
        var lambda = new MethodNode(ACC_PRIVATE | ACC_STATIC, "renamedLambda",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;FLnet/silentchaos512/gear/api/traits/TraitInstance;Ljava/lang/Float;)Ljava/lang/Float;", null, null);
        owner.methods.add(lambda); var c = lambda.instructions; String p = SilentGearDefenseShape.P;
        c.add(new VarInsnNode(ALOAD, 4)); c.add(new MethodInsnNode(INVOKEVIRTUAL, p + "api/traits/TraitInstance", "getTrait", "()L" + p + "gear/trait/Trait;", false));
        c.add(new VarInsnNode(ALOAD, 0)); c.add(new VarInsnNode(ALOAD, 4)); c.add(new MethodInsnNode(INVOKEVIRTUAL, p + "api/traits/TraitInstance", "getLevel", "()I", false));
        c.add(new VarInsnNode(ALOAD, 1)); c.add(new VarInsnNode(ALOAD, 2)); c.add(new VarInsnNode(ALOAD, 5));
        c.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
        var base = new VarInsnNode(FLOAD, 3); c.add(base);
        c.add(new MethodInsnNode(INVOKEVIRTUAL, p + "gear/trait/Trait", "onEntityIncomingDamage", SilentGearDefenseShape.NEGATION, false));
        c.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false)); c.add(new InsnNode(ARETURN));
        assertTrue(SilentGearDefenseShape.incomingLambda(owner, lambda));
        c.insert(new InsnNode(NOP)); assertTrue(SilentGearDefenseShape.incomingLambda(owner, lambda));
        c.set(base, new InsnNode(FCONST_0)); assertFalse(SilentGearDefenseShape.incomingLambda(owner, lambda));
    }

    @Test void incomingBridgeRequiresReadOnlyCaptureAndGuardedOwnedRouting() {
        var owner = new ClassNode(); owner.name = SilentGearDefenseShape.EVENTS;
        String prefix = "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Object;Ljava/lang/Object;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;";
        var inner = new MethodNode(ACC_PRIVATE | ACC_STATIC, "ownedIncoming", prefix + "F)Ljava/lang/Object;", null, null); owner.methods.add(inner);
        for (int i = 0; i < 5; i++) { inner.instructions.add(new VarInsnNode(ALOAD, i)); } inner.instructions.add(new VarInsnNode(FLOAD, 5));
        inner.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedboots$sgIncomingReady", "()Z", false));
        var route = new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearDefense", "incoming", prefix + "FZ)Ljava/lang/Object;", false);
        inner.instructions.add(route); inner.instructions.add(new InsnNode(ARETURN));
        var bridge = new MethodNode(ACC_PRIVATE | ACC_STATIC, "renamedBridge", prefix + "Lcom/llamalad7/mixinextras/sugar/ref/LocalFloatRef;)Ljava/lang/Object;", null, null); owner.methods.add(bridge);
        for (int i = 0; i < 6; i++) { bridge.instructions.add(new VarInsnNode(ALOAD, i)); }
        var get = new MethodInsnNode(INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalFloatRef", "get", "()F", true); bridge.instructions.add(get);
        bridge.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, inner.name, inner.desc, false)); bridge.instructions.add(new InsnNode(ARETURN));
        var site = new MethodInsnNode(INVOKESTATIC, owner.name, bridge.name, bridge.desc, false);
        assertTrue(SilentGearDefenseShape.incomingBridge(owner, site));
        get.name = "foreignRead"; assertFalse(SilentGearDefenseShape.incomingBridge(owner, site)); get.name = "get";
        route.name = "unsafeDispatcher"; assertFalse(SilentGearDefenseShape.incomingBridge(owner, site));
    }

    @Test void selectedMathAllowsUnrelatedEditsButRefusesChangedDeltaOrActorAccess() {
        var owner = negate(); assertTrue(SilentGearDefenseShape.negate(owner));
        owner.methods.getFirst().instructions.insert(new InsnNode(NOP)); owner.methods.add(new MethodNode(ACC_PRIVATE, "unrelated", "()V", null, null));
        assertTrue(SilentGearDefenseShape.negate(owner));
        var method = owner.methods.getFirst();
        for (var n : method.instructions) { if (n.getOpcode() == FSUB) { method.instructions.set(n, new InsnNode(FADD)); break; } }
        assertFalse(SilentGearDefenseShape.negate(owner));
        owner = negate(); owner.methods.getFirst().instructions.insert(new VarInsnNode(ALOAD, 3));
        assertFalse(SilentGearDefenseShape.negate(owner));
    }

    @Test void refusesDifferentScaleTagAndClampConsumer() {
        var owner = negate();
        for (var n : owner.methods.getFirst().instructions) { if (n instanceof FieldInsnNode f && f.name.equals("damageType")) { f.name = "foreignTag"; } }
        assertFalse(SilentGearDefenseShape.negate(owner)); owner = negate();
        for (var n : owner.methods.getFirst().instructions) { if (n instanceof MethodInsnNode c && c.name.equals("clamp")) { c.owner = "foreign/UnprovedMath"; } }
        assertFalse(SilentGearDefenseShape.negate(owner));
    }

    @Test void admissionBoundsDerivedReductionWithoutInventingStockBalanceLimits() {
        assertTrue(SilentGearDefenseApi.parameters(0.04F, 4)); assertTrue(SilentGearDefenseApi.parameters(0, 5)); assertTrue(SilentGearDefenseApi.parameters(2, 1));
        assertFalse(SilentGearDefenseApi.parameters(-0.1F, 4)); assertFalse(SilentGearDefenseApi.parameters(Float.NaN, 1));
        assertFalse(SilentGearDefenseApi.parameters(Float.MAX_VALUE, 127)); assertFalse(SilentGearDefenseApi.parameters(0.2F, 0));
        assertFalse(SilentGearDefenseApi.parameters(0.2F, 128));
    }

    private static ClassNode negate() {
        var owner = new ClassNode(); owner.name = SilentGearDefenseShape.NEGATE;
        var method = new MethodNode(ACC_PUBLIC, "onEntityIncomingDamage", SilentGearDefenseShape.NEGATION, null, null); owner.methods.add(method);
        var c = method.instructions; var end = new LabelNode();
        c.add(new VarInsnNode(ALOAD, 4)); c.add(new VarInsnNode(ALOAD, 0)); c.add(new FieldInsnNode(GETFIELD, owner.name, "damageType", "Lnet/minecraft/tags/TagKey;"));
        c.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/damagesource/DamageSource", "is", "(Lnet/minecraft/tags/TagKey;)Z", false)); c.add(new JumpInsnNode(IFEQ, end));
        c.add(new VarInsnNode(FLOAD, 6)); c.add(new VarInsnNode(ALOAD, 0)); c.add(new FieldInsnNode(GETFIELD, owner.name, "negatedDamageScale", "F")); c.add(new InsnNode(FMUL));
        c.add(new VarInsnNode(ILOAD, 2)); c.add(new InsnNode(I2F)); c.add(new InsnNode(FMUL)); c.add(new VarInsnNode(FSTORE, 9));
        c.add(new VarInsnNode(FLOAD, 5)); c.add(new VarInsnNode(FLOAD, 9)); c.add(new InsnNode(FSUB)); c.add(new InsnNode(FCONST_0)); c.add(new VarInsnNode(FLOAD, 5));
        c.add(new MethodInsnNode(INVOKESTATIC, "net/minecraft/util/Mth", "clamp", "(FFF)F", false)); c.add(new InsnNode(FRETURN)); c.add(end); c.add(new VarInsnNode(FLOAD, 5)); c.add(new InsnNode(FRETURN)); return owner;
    }
}
