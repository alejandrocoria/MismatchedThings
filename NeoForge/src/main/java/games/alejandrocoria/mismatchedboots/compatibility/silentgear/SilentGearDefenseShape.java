package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selected defensive math and local branch/payment boundaries, never a class/JAR fingerprint. */
final class SilentGearDefenseShape implements Opcodes {
    static final String P = SilentGearEffectShape.PREFIX, EVENTS = P + "event/GearEvents", NEGATE = P + "gear/trait/effect/NegateDamageTraitEffect";
    static final String SCHEDULER = "net/silentchaos512/lib/event/ServerTicks", STACK = "net/minecraft/world/item/ItemStack";
    static final String ACTOR = "net/minecraft/world/entity/LivingEntity", ENTITY = "net/minecraft/world/entity/Entity", SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/LivingFallEvent", VEC = "net/minecraft/world/phys/Vec3";
    static final String FALL = "(L" + EVENT + ";)V", WEAR = "(L" + STACK + ";IL" + ACTOR + ";L" + SLOT + ";)V";
    static final String CONDITION = WEAR.substring(0, WEAR.length() - 1) + "Z";
    static final String NEGATION = "(L" + STACK + ";IL" + ACTOR + ";Lnet/minecraft/world/damagesource/DamageSource;FF)F";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/silentgear/SilentGearDefense";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private SilentGearDefenseShape() {}

    static void publish(ClassNode owner) {
        if (owner.name.equals(NEGATE)) { SilentGearEffectShape.publish(owner, "mismatchedboots$sgNegateReady", negate(owner)); }
        if (owner.name.equals(SCHEDULER)) { SilentGearEffectShape.publish(owner, "mismatchedboots$sgScheduleReady", scheduler(owner)); }
        if (owner.name.equals(EVENTS)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgSafeFallReady", fall(owner));
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgIncomingReady", incoming(owner));
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgRetaliationReady", retaliation(owner));
        }
    }

    static MethodInsnNode wearSite(InsnList instructions) {
        var matches = code(instructions).stream().filter(node -> call(node, P + "util/GearHelper", "attemptDamage", WEAR)).toList();
        return matches.size() == 1 ? (MethodInsnNode) matches.getFirst() : null;
    }

    static boolean negate(ClassNode owner) {
        var m = method(owner, "onEntityIncomingDamage", NEGATION); if (m == null || !m.tryCatchBlocks.isEmpty()) { return false; }
        var c = code(m.instructions);
        // This concrete leaf never reads stack/actor arguments. Require the native scale and clamp producers.
        if (c.size() != 22 || !load(c.get(0), ALOAD, 4) || !load(c.get(1), ALOAD, 0)
                || !field(c.get(2), GETFIELD, NEGATE, "damageType", "Lnet/minecraft/tags/TagKey;")
                || !call(c.get(3), "net/minecraft/world/damagesource/DamageSource", "is", "(Lnet/minecraft/tags/TagKey;)Z")
                || !(c.get(4) instanceof JumpInsnNode skip) || skip.getOpcode() != IFEQ
                || !load(c.get(5), FLOAD, 6) || !load(c.get(6), ALOAD, 0)
                || !field(c.get(7), GETFIELD, NEGATE, "negatedDamageScale", "F") || c.get(8).getOpcode() != FMUL
                || !load(c.get(9), ILOAD, 2) || c.get(10).getOpcode() != I2F || c.get(11).getOpcode() != FMUL
                || !(c.get(12) instanceof VarInsnNode delta) || delta.getOpcode() != FSTORE
                || !load(c.get(13), FLOAD, 5) || !load(c.get(14), FLOAD, delta.var) || c.get(15).getOpcode() != FSUB
                || c.get(16).getOpcode() != FCONST_0 || !load(c.get(17), FLOAD, 5)
                || !call(c.get(18), "net/minecraft/util/Mth", "clamp", "(FFF)F")) { return false; }
        return c.get(19).getOpcode() == FRETURN && load(c.get(20), FLOAD, 5) && c.get(21).getOpcode() == FRETURN
                && m.instructions.indexOf(skip.label) > m.instructions.indexOf(c.get(19))
                && m.instructions.indexOf(skip.label) < m.instructions.indexOf(c.get(20));
    }

    static boolean fall(ClassNode owner) {
        var m = method(owner, "onLivingFall", FALL); if (m == null || !m.tryCatchBlocks.isEmpty()) { return false; }
        var c = code(m.instructions); var gates = c.stream().filter(n -> n instanceof MethodInsnNode call && call.owner.equals(EVENTS) && call.name.contains("mismatchedboots$sgFallGate")).toList();
        var wear = wearSite(m.instructions);
        if (gates.size() != 1 || wear == null) { return false; }
        var gate = (MethodInsnNode) gates.getFirst(); if (!branchGate(owner, m, gate)) { return false; }
        int g = c.indexOf(gate), w = c.indexOf(wear);
        int query = find(c, P + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY);
        // Select by the Bounce ID, independently of Moonwalker's earlier query and harmless result peers.
        for (int i = 2; i < g; i++) { if (resource(c.get(i - 1), "BOUNCE") && call(c.get(i), P + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY)) { query = i; } }
        if (query < 2 || !call(c.get(query), P + "util/TraitHelper", "getHighestLevelArmorOrCurio", SilentGearRecognitionShape.QUERY)
                || !resource(c.get(query - 1), "BOUNCE") || !(c.get(query - 2) instanceof VarInsnNode player) || player.getOpcode() != ALOAD) { return false; }
        int rebound = find(c, EVENTS, "bounceEntity", "(L" + ENTITY + ";)V");
        if (rebound <= g || w <= rebound || !positiveBill(c, rebound, w) || !wearCondition(owner, m, c, w)
                || reaches(m.instructions.getFirst(), c.get(rebound), gate) || !paymentOrigins(owner, m, c, query, rebound, wear)) { return false; }
        // Verify only the selected branch: Moonwalker/unrelated methods are independent.
        var allowed = Set.of(CALLBACK + ".isCancelled()Z", EVENT + ".getEntity()L" + ACTOR + ";", ACTOR + ".isSuppressingBounce()Z", ACTOR + ".isFallFlying()Z",
                EVENTS + ".bounceEntity(L" + ENTITY + ";)V", EVENT + ".getDistance()D", P + "util/GearHelper.attemptDamage" + WEAR,
                ACTOR + ".level()Lnet/minecraft/world/level/Level;", ACTOR + ".blockPosition()Lnet/minecraft/core/BlockPos;",
                "net/minecraft/world/level/Level.playSound(L" + ENTITY + ";Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
                EVENT + ".setCanceled(Z)V");
        for (int i = g + 1; i < c.size(); i++) {
            var n = c.get(i);
            if (n instanceof MethodInsnNode call && !allowed.contains(key(call)) && !call.name.contains("mismatchedboots$sgFallWear")) { return false; }
            if (n.getOpcode() == PUTFIELD || n.getOpcode() == PUTSTATIC || n.getOpcode() >= IASTORE && n.getOpcode() <= SASTORE || n instanceof InvokeDynamicInsnNode) { return false; }
        }
        // All preconditions must exit beyond the complete side-effect branch.
        for (String guard : List.of("isSuppressingBounce", "isFallFlying")) {
            int at = find(c, ACTOR, guard, "()Z");
            if (at < g || at > rebound || !(c.get(at + 1) instanceof JumpInsnNode skip) || skip.getOpcode() != IFNE
                    || m.instructions.indexOf(skip.label) <= m.instructions.indexOf(wear)) { return false; }
        }
        return bounce(owner);
    }

    private static boolean branchGate(ClassNode owner, MethodNode method, MethodInsnNode gate) {
        var body = method(owner, gate.name, gate.desc); if (body == null || !body.tryCatchBlocks.isEmpty()) { return false; }
        var c = code(body.instructions);
        if (c.size() != 7 || !load(c.get(0), ALOAD, 0) || !call(c.get(1), EVENTS, "mismatchedboots$sgSafeFallReady", "()Z")
                || !call(c.get(2), ADAPTER, "allowFall", "(L" + EVENT + ";Z)Z") || !(c.get(3) instanceof JumpInsnNode allowed) || allowed.getOpcode() != IFNE
                || !load(c.get(4), ALOAD, 1) || !call(c.get(5), CALLBACK, "cancel", "()V") || c.getLast().getOpcode() != RETURN
                || body.instructions.indexOf(allowed.label) <= body.instructions.indexOf(c.get(5))) { return false; }
        var nativeCode = code(method.instructions); int at = nativeCode.indexOf(gate);
        if (!(at + 4 < nativeCode.size() && nativeCode.get(at + 1) instanceof VarInsnNode callback && callback.getOpcode() == ALOAD
                && call(nativeCode.get(at + 2), CALLBACK, "isCancelled", "()Z") && nativeCode.get(at + 3) instanceof JumpInsnNode proceed && proceed.getOpcode() == IFEQ
                && nativeCode.get(at + 4).getOpcode() == RETURN)) { return false; }
        try {
            var flow = new Flow(owner, method); var callbackSource = flow.origin(flow.argument(gate, 1, 2));
            if (!flow.parameter(flow.argument(gate, 0, 2), 0) || callbackSource != flow.origin(flow.argument(nativeCode.get(at + 2), 0, 1))) { return false; }
            for (var n : nativeCode) {
                if (call(n, CALLBACK, "<init>", "(Ljava/lang/String;Z)V") && flow.origin(flow.argument(n, 0, 3)) == callbackSource) {
                    var enabled = flow.origin(flow.argument(n, 1, 2)); return enabled != null && enabled.getOpcode() == ICONST_1;
                }
            }
            return false;
        } catch (AnalyzerException | RuntimeException invalid) { return false; }
    }

    private static boolean positiveBill(List<AbstractInsnNode> c, int rebound, int wear) {
        if (rebound + 11 >= wear) { return false; }
        return load(c.get(rebound + 1), ALOAD, 0) && call(c.get(rebound + 2), EVENT, "getDistance", "()D")
                && number(c.get(rebound + 3), 3D) && c.get(rebound + 4).getOpcode() == DDIV && c.get(rebound + 5).getOpcode() == D2I
                && c.get(rebound + 6).getOpcode() == ICONST_1 && c.get(rebound + 7).getOpcode() == ISUB
                && c.get(rebound + 8) instanceof VarInsnNode bill && bill.getOpcode() == ISTORE
                && load(c.get(rebound + 9), ILOAD, bill.var) && c.get(rebound + 10) instanceof JumpInsnNode guard && guard.getOpcode() == IFLE
                && !reaches(guard.label, c.get(wear), null);
    }

    private static boolean wearCondition(ClassNode owner, MethodNode method, List<AbstractInsnNode> c, int wear) {
        int found = 0;
        for (int i = 0; i + 1 < wear; i++) {
            if (!(c.get(i) instanceof MethodInsnNode hook) || !hook.owner.equals(EVENTS) || !hook.name.contains("mismatchedboots$sgFallWear") || !hook.desc.equals(CONDITION)) { continue; }
            if (!(c.get(i + 1) instanceof JumpInsnNode skip) || skip.getOpcode() != IFEQ
                    || reaches(skip.label, c.get(wear), null) || reaches(method.instructions.getFirst(), c.get(wear), hook)
                    || !routing(owner, hook, "allowNativeFallWear", CONDITION.substring(0, CONDITION.length() - 2) + "Z)Z", new int[]{ALOAD, ILOAD, ALOAD, ALOAD})) { return false; }
            found++;
        }
        return found == 1;
    }

    private static boolean paymentOrigins(ClassNode owner, MethodNode m, List<AbstractInsnNode> c, int query, int rebound, MethodInsnNode wear) {
        try {
            var flow = new Flow(owner, m);
            var feet = flow.origin(flow.argument(wear, 0, 4)); var actor = flow.origin(flow.argument(wear, 2, 4));
            if (!call(feet, ACTOR, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";")
                    || !field(flow.origin(flow.argument(feet, 0, 1)), GETSTATIC, SLOT, "FEET", "L" + SLOT + ";")
                    || !call(actor, EVENT, "getEntity", "()L" + ACTOR + ";") || !flow.parameter(flow.argument(actor, 0, 1), 0)
                    || !call(flow.origin(flow.argument(feet, 0, 2)), EVENT, "getEntity", "()L" + ACTOR + ";")
                    || !field(flow.origin(flow.argument(wear, 3, 4)), GETSTATIC, SLOT, "FEET", "L" + SLOT + ";")
                    || flow.origin(flow.argument(wear, 1, 4)) != c.get(rebound + 7)) { return false; }
            var queried = flow.origin(flow.argument(c.get(query), 0, 2));
            if (!call(queried, EVENT, "getEntity", "()L" + ACTOR + ";") || !flow.parameter(flow.argument(queried, 0, 1), 0)) { return false; }
            var playerChecks = c.subList(0, query).stream().filter(n -> n instanceof TypeInsnNode t && t.getOpcode() == INSTANCEOF && t.desc.equals("net/minecraft/world/entity/player/Player")).toList();
            if (playerChecks.size() != 1) { return false; } int check = c.indexOf(playerChecks.getFirst());
            if (!(c.get(check + 1) instanceof JumpInsnNode notPlayer) || notPlayer.getOpcode() != IFEQ || reaches(notPlayer.label, c.get(rebound), null)) { return false; }
            int shortGuard = -1;
            for (int i = 0; i + 3 < query; i++) {
                if (call(c.get(i), EVENT, "getDistance", "()D") && number(c.get(i + 1), 3D) && c.get(i + 2).getOpcode() == DCMPL
                        && c.get(i + 3) instanceof JumpInsnNode branch && branch.getOpcode() == IFLE && !reaches(branch.label, c.get(rebound), null)) { shortGuard = i; }
            }
            if (shortGuard < 0) { return false; }
            for (var n : c) {
                if (n instanceof MethodInsnNode hook && hook.name.contains("mismatchedboots$sgFallWear")) {
                    for (int i = 0; i < 4; i++) { if (flow.origin(flow.argument(hook, i, 4)) != flow.origin(flow.argument(wear, i, 4))) { return false; } }
                }
            }
            return true;
        } catch (AnalyzerException | RuntimeException invalid) { return false; }
    }

    private static boolean routing(ClassNode owner, MethodInsnNode hook, String name, String descriptor, int[] loads) {
        var m = method(owner, hook.name, hook.desc); if (m == null || !m.tryCatchBlocks.isEmpty()) { return false; }
        var c = code(m.instructions); if (c.size() != loads.length + 3) { return false; }
        for (int i = 0; i < loads.length; i++) { if (!load(c.get(i), loads[i], i)) { return false; } }
        return call(c.get(loads.length), EVENTS, "mismatchedboots$sgSafeFallReady", "()Z")
                && call(c.get(loads.length + 1), ADAPTER, name, descriptor) && c.getLast().getOpcode() == IRETURN;
    }

    static boolean incoming(ClassNode owner) {
        String event = "net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent";
        var m = method(owner, "onLivingIncomingDamage", "(L" + event + ";)V"); if (m == null) { return false; }
        var c = code(m.instructions); int selected = -1;
        for (int i = 0; i < c.size(); i++) { if (c.get(i) instanceof TypeInsnNode type && type.getOpcode() == INSTANCEOF && type.desc.equals(P + "api/item/GearArmor")) { if (selected >= 0) { return false; } selected = i; } }
        if (selected < 3 || selected + 16 >= c.size() || !(c.get(selected - 2) instanceof VarInsnNode stack) || stack.getOpcode() != ALOAD
                || !call(c.get(selected - 1), STACK, "getItem", "()Lnet/minecraft/world/item/Item;")) { return false; }
        // The local invocation captures this stack, entity/source and a separate common-base lane.
        for (int i = selected + 1; i + 4 < c.size(); i++) {
            if (!(c.get(i) instanceof InvokeDynamicInsnNode dynamic)) { continue; }
            if (!dynamic.desc.equals("(L" + STACK + ";L" + ACTOR + ";Lnet/minecraft/world/damagesource/DamageSource;F)L" + P + "api/traits/TraitFunction;")) { return false; }
            var lambda = lambda(owner, dynamic); if (lambda == null) { return false; }
            boolean commonBase;
            try {
                var flow = new Flow(owner, m); var base = flow.origin(flow.argument(dynamic, 3, 4));
                commonBase = call(base, event, "getAmount", "()F") && flow.parameter(flow.argument(base, 0, 1), 0)
                        && m.instructions.indexOf(base) < m.instructions.indexOf(c.get(selected))
                        && flow.origin(flow.argument(dynamic, 0, 4)) == flow.origin(flow.argument(c.get(i + 1), 0, 3));
            } catch (AnalyzerException | RuntimeException invalid) { return false; }
            return commonBase && c.get(i - 1) instanceof VarInsnNode base && base.getOpcode() == FLOAD && load(c.get(i - 4), ALOAD, stack.var)
                    && call(c.get(i + 1), P + "util/TraitHelper", "activateTraits", "(L" + STACK + ";Ljava/lang/Object;L" + P + "api/traits/TraitFunction;)Ljava/lang/Object;")
                    && readsOnly(lambda, Set.of(P + "api/traits/TraitInstance.getTrait()L" + P + "gear/trait/Trait;", P + "api/traits/TraitInstance.getLevel()I",
                        "java/lang/Float.floatValue()F", "java/lang/Float.valueOf(F)Ljava/lang/Float;", P + "gear/trait/Trait.onEntityIncomingDamage" + NEGATION));
        }
        return false;
    }

    static boolean retaliation(ClassNode owner) {
        String event = "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Post";
        var m = method(owner, "onPlayerHurt", "(L" + event + ";)V"); if (m == null) { return false; }
        var c = code(m.instructions); int query = find(c, P + "util/TraitHelper", "getHighestLevelArmor", SilentGearRecognitionShape.QUERY);
        int hit = find(c, ACTOR, "knockback", "(DDD)V");
        if (query <= 1 || hit < 0 || !resource(c.get(query - 1), "BOUNCE") || !(c.get(query + 3) instanceof JumpInsnNode positive) || positive.getOpcode() != IFLE
                || reaches(positive.label, c.get(hit), null)) { return false; }
        try {
            var flow = new Flow(owner, m); var direct = flow.origin(flow.argument(c.get(hit), 0, 4));
            if (!call(direct, "net/minecraft/world/damagesource/DamageSource", "getDirectEntity", "()L" + ENTITY + ";")) { return false; }
            var source = flow.origin(flow.argument(direct, 0, 1));
            if (!call(source, event, "getSource", "()Lnet/minecraft/world/damagesource/DamageSource;") || !flow.parameter(flow.argument(source, 0, 1), 0)) { return false; }
            var strength = flow.origin(flow.argument(c.get(hit), 0, 3)); if (strength == null || strength.getOpcode() != I2D) { return false; }
            var product = flow.origin(flow.argument(strength, 0, 1)); if (product == null || product.getOpcode() != IMUL
                    || flow.origin(flow.argument(product, 0, 2)).getOpcode() != ICONST_2 || flow.origin(flow.argument(product, 1, 2)) != c.get(query)) { return false; }
            for (var n : c) {
                if (call(n, ENTITY, "getYRot", "()F") && flow.origin(flow.argument(n, 0, 1)) != direct) { return false; }
            }
            for (String type : List.of("net/minecraft/world/entity/player/Player", ACTOR)) {
                var checks = c.stream().filter(n -> n instanceof TypeInsnNode t && t.getOpcode() == INSTANCEOF && t.desc.equals(type)).toList();
                if (checks.size() != 1) { return false; } int at = c.indexOf(checks.getFirst());
                if (!(c.get(at + 1) instanceof JumpInsnNode skip) || skip.getOpcode() != IFEQ || reaches(skip.label, c.get(hit), null)) { return false; }
            }
        } catch (AnalyzerException | RuntimeException invalid) { return false; }
        return count(c, ACTOR, "knockback", "(DDD)V") == 1
                && readsOnly(m, Set.of(event + ".getEntity()L" + ACTOR + ";", event + ".getSource()Lnet/minecraft/world/damagesource/DamageSource;",
                    "net/minecraft/world/damagesource/DamageSource.getDirectEntity()L" + ENTITY + ";", P + "util/TraitHelper.getHighestLevelArmor" + SilentGearRecognitionShape.QUERY,
                    "org/apache/logging/log4j/Logger.debug(Ljava/lang/String;)V", ENTITY + ".getYRot()F", "net/minecraft/util/Mth.sin(D)F", "net/minecraft/util/Mth.cos(D)F", ACTOR + ".knockback(DDD)V"));
    }

    private static boolean bounce(ClassNode owner) {
        var m = method(owner, "bounceEntity", "(L" + ENTITY + ";)V"); if (m == null || !m.tryCatchBlocks.isEmpty()) { return false; }
        var c = code(m.instructions); int queued = find(c, SCHEDULER, "scheduleAction", "(Ljava/lang/Runnable;)V");
        if (queued < 1 || !(c.get(queued - 1) instanceof InvokeDynamicInsnNode dynamic)) { return false; }
        var action = lambda(owner, dynamic);
        if (action == null || c.size() != 23 || !dynamic.desc.equals("(L" + ENTITY + ";L" + VEC + ";)Ljava/lang/Runnable;")) { return false; }
        var a = code(action.instructions);
        if (a.size() != 4 || !load(a.get(0), ALOAD, 0) || !load(a.get(1), ALOAD, 1) || !call(a.get(2), ENTITY, "push", "(L" + VEC + ";)V") || a.get(3).getOpcode() != RETURN) { return false; }
        return load(c.get(0), ALOAD, 0) && call(c.get(1), ENTITY, "getDeltaMovement", "()L" + VEC + ";")
                && c.get(2) instanceof VarInsnNode velocity && velocity.getOpcode() == ASTORE
                && load(c.get(3), ALOAD, velocity.var) && field(c.get(4), GETFIELD, VEC, "y", "D") && c.get(5).getOpcode() == DCONST_0 && c.get(6).getOpcode() == DCMPG
                && c.get(7) instanceof JumpInsnNode skip && skip.getOpcode() == IFGE && !reaches(skip.label, c.get(queued), null)
                && c.get(8) instanceof TypeInsnNode allocation && allocation.getOpcode() == NEW && allocation.desc.equals(VEC) && c.get(9).getOpcode() == DUP
                && c.get(10).getOpcode() == DCONST_0 && number(c.get(11), -0.75D) && load(c.get(12), ALOAD, velocity.var)
                && field(c.get(13), GETFIELD, VEC, "y", "D") && c.get(14).getOpcode() == DMUL && c.get(15).getOpcode() == DCONST_0
                && call(c.get(16), VEC, "<init>", "(DDD)V") && c.get(17) instanceof VarInsnNode push && push.getOpcode() == ASTORE
                && load(c.get(18), ALOAD, 0) && load(c.get(19), ALOAD, push.var) && c.get(22).getOpcode() == RETURN;
    }

    static boolean scheduler(ClassNode owner) {
        var drain = method(owner, "runScheduledActions", "()V");
        var tick = method(owner, "serverTicks", "(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Pre;)V");
        var schedule = method(owner, "scheduleAction", "(Ljava/lang/Runnable;)V");
        if (schedule == null || owner.fields.stream().noneMatch(f -> f.name.equals("scheduledActions") && f.desc.equals("Ljava/util/Queue;") && (f.access & ACC_PRIVATE) != 0)) { return false; }
        if (!readsOnly(schedule, Set.of("java/util/Queue.add(Ljava/lang/Object;)Z", "java/util/Queue.size()I", "java/lang/Integer.valueOf(I)Ljava/lang/Integer;",
                "org/slf4j/Logger.warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", "org/slf4j/Logger.error(Ljava/lang/String;Ljava/lang/Throwable;)V",
                "java/lang/IllegalStateException.<init>(Ljava/lang/String;)V", "java/util/Queue.clear()V"))) { return false; }
        try {
            var flow = new Flow(owner, schedule); var c = code(schedule.instructions); int add = find(c, "java/util/Queue", "add", "(Ljava/lang/Object;)Z");
            if (add < 0 || count(c, "java/util/Queue", "add", "(Ljava/lang/Object;)Z") != 1 || !flow.parameter(flow.argument(c.get(add), 0, 1), 0)) { return false; }
        } catch (AnalyzerException | RuntimeException invalid) { return false; }
        return drain != null && tick != null && readsOnly(tick, Set.of(SCHEDULER + ".runScheduledActions()V"))
                && readsOnly(drain, Set.of("java/util/Queue.poll()Ljava/lang/Object;", "java/lang/Runnable.run()V"))
                && count(code(drain.instructions), "java/lang/Runnable", "run", "()V") == 1
                && count(code(drain.instructions), "java/util/Queue", "poll", "()Ljava/lang/Object;") == 2;
    }

    private static MethodNode lambda(ClassNode owner, InvokeDynamicInsnNode dynamic) {
        if (!dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !dynamic.bsm.getName().equals("metafactory")
                || dynamic.bsmArgs.length != 3 || !(dynamic.bsmArgs[1] instanceof Handle target) || target.getTag() != H_INVOKESTATIC || !target.getOwner().equals(owner.name)) { return null; }
        return method(owner, target.getName(), target.getDesc());
    }
    private static boolean readsOnly(MethodNode m, Set<String> calls, InvokeDynamicInsnNode... dynamics) {
        if (!m.tryCatchBlocks.isEmpty()) { return false; }
        for (var n : m.instructions) {
            if (n instanceof MethodInsnNode call && !calls.contains(key(call)) || n instanceof InvokeDynamicInsnNode dynamic && !List.of(dynamics).contains(dynamic)
                    || n.getOpcode() == PUTFIELD || n.getOpcode() == PUTSTATIC || n.getOpcode() >= IASTORE && n.getOpcode() <= SASTORE) { return false; }
        }
        return true;
    }
    private static boolean reaches(AbstractInsnNode start, AbstractInsnNode end, AbstractInsnNode blocked) {
        var pending = new ArrayDeque<AbstractInsnNode>(); var seen = new HashSet<AbstractInsnNode>(); pending.add(start);
        while (!pending.isEmpty()) {
            var n = pending.removeFirst(); if (n == blocked || !seen.add(n)) { continue; } if (n == end) { return true; }
            if (n instanceof JumpInsnNode j) { pending.add(j.label); }
            if (n instanceof TableSwitchInsnNode s) { pending.add(s.dflt); pending.addAll(s.labels); }
            if (n instanceof LookupSwitchInsnNode s) { pending.add(s.dflt); pending.addAll(s.labels); }
            int op = n.getOpcode();
            if (op != GOTO && op != ATHROW && (op < IRETURN || op > RETURN) && !(n instanceof TableSwitchInsnNode) && !(n instanceof LookupSwitchInsnNode) && n.getNext() != null) { pending.add(n.getNext()); }
        }
        return false;
    }
    /** Local provenance through MixinExtras spill locals; unknown/merged origins are refused. */
    private static final class Flow {
        private final MethodNode method;
        private final Frame<SourceValue>[] frames;
        Flow(ClassNode owner, MethodNode method) throws AnalyzerException {
            this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                    int op = instruction.getOpcode(); return op >= DUP && op <= SWAP ? value : super.copyOperation(instruction, value);
                }
            }).analyzeAndComputeMaxs(owner.name, method);
        }
        private Frame<SourceValue> at(AbstractInsnNode n) { return frames[method.instructions.indexOf(n)]; }
        SourceValue argument(AbstractInsnNode n, int index, int count) { var f = at(n); return f == null ? null : f.getStack(f.getStackSize() - count + index); }
        AbstractInsnNode origin(SourceValue value) { return origin(value, new HashSet<>()); }
        private AbstractInsnNode origin(SourceValue value, Set<AbstractInsnNode> seen) {
            if (value == null || value.insns.size() != 1) { return null; } var n = value.insns.iterator().next(); if (!seen.add(n)) { return null; }
            if (n instanceof VarInsnNode v && n.getOpcode() >= ILOAD && n.getOpcode() <= ALOAD) { var old = at(n).getLocal(v.var); return old.insns.isEmpty() ? n : origin(old, seen); }
            if (n instanceof VarInsnNode && n.getOpcode() >= ISTORE && n.getOpcode() <= ASTORE || n.getOpcode() == CHECKCAST) { return origin(argument(n, 0, 1), seen); }
            return n;
        }
        boolean parameter(SourceValue value, int slot) { var n = origin(value); return n instanceof VarInsnNode v && v.var == slot && at(n).getLocal(slot).insns.isEmpty(); }
    }
    static List<AbstractInsnNode> code(InsnList instructions) { return Arrays.stream(instructions.toArray()).filter(n -> n.getOpcode() >= 0 && n.getOpcode() != NOP).toList(); }
    static MethodNode method(ClassNode owner, String name, String desc) { var found = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList(); return found.size() == 1 ? found.getFirst() : null; }
    private static boolean call(AbstractInsnNode n, String owner, String name, String desc) { return n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc); }
    private static int find(List<AbstractInsnNode> c, String owner, String name, String desc) { for (int i = 0; i < c.size(); i++) { if (call(c.get(i), owner, name, desc)) { return i; } } return -1; }
    private static long count(List<AbstractInsnNode> c, String owner, String name, String desc) { return c.stream().filter(n -> call(n, owner, name, desc)).count(); }
    private static boolean load(AbstractInsnNode n, int opcode, int slot) { return n instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == slot; }
    private static boolean field(AbstractInsnNode n, int opcode, String owner, String name, String desc) { return n instanceof FieldInsnNode f && f.getOpcode() == opcode && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc); }
    private static boolean resource(AbstractInsnNode n, String name) { return field(n, GETSTATIC, P + "util/Const$Traits", name, SilentGearRecognitionShape.RESOURCE); }
    private static boolean number(AbstractInsnNode n, double value) { return n instanceof LdcInsnNode ldc && ldc.cst instanceof Double d && d == value; }
    private static String key(MethodInsnNode c) { return c.owner + "." + c.name + c.desc; }
}
