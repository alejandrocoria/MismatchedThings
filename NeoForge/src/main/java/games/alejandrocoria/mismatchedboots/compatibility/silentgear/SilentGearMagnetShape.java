package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Local reachable calls/writes and native queue arbitration, not a whole-class fingerprint. */
final class SilentGearMagnetShape implements Opcodes {
    static final String EFFECT = SilentGearEffectShape.PREFIX + "gear/trait/effect/ItemMagnetTraitEffect";
    static final String TRACKER = SilentGearEffectShape.PREFIX + "gear/util/MagnetPullTracker";
    static final String PLAYER = "net/minecraft/world/entity/player/Player", ITEM = "net/minecraft/world/entity/item/ItemEntity";
    static final String VEC = "net/minecraft/world/phys/Vec3", LEVEL = "net/minecraft/world/level/Level";
    static final String PUSH = "(L" + ITEM + ";L" + VEC + ";)V";
    static final String META = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final String CTX = SilentGearEffectShape.CONTEXT, STACK = SilentGearEffectShape.STACK;
    private static final String QUEUE = TRACKER + ".ITEMS_TO_PUSH:Ljava/util/Map;";
    private static final Set<String> EFFECT_FIELDS = Set.of(EFFECT + ".pullStrength:F", EFFECT + ".effectRange:F",
                                                            EFFECT + ".affectedItems:Ljava/util/Optional;", VEC + ".x:D", VEC + ".y:D", VEC + ".z:D",
                                                            SilentGearEffectShape.PREFIX + "Config$Common.magnetPullDisabledOnCrouch:Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;");

    private SilentGearMagnetShape() {
    }

    static void publish(ClassNode owner) {
        if (owner.name.equals(EFFECT)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgMagnetReady", effect(owner));
        }
        if (owner.name.equals(TRACKER)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgMagnetQueueReady", tracker(owner));
        }
    }

    static boolean effect(ClassNode owner) {
        String tickMagnet = "(L" + PLAYER + ";I)V";
        var entry = method(owner, "inventoryTick", SilentGearEffectShape.TICK);
        var pull = method(owner, "tickMagnet", tickMagnet);
        return entry != null && pull != null && radius(pull)
                && calls(entry, EFFECT + ".tickMagnet" + tickMagnet) == 1
                && calls(pull, TRACKER + ".pushItem" + PUSH) == 1
                && forward(entry)
                && body(owner,
                        entry,
                        Set.of(CTX + ".player()L" + PLAYER + ";", CTX + ".traitLevel()I",
                               PLAYER + ".level()L" + LEVEL + ";", LEVEL + ".isClientSide()Z", EFFECT + ".tickMagnet" + tickMagnet),
                        EFFECT_FIELDS,
                        null)
                && body(owner, pull, Set.of(EFFECT + ".isCrouchDisabled(L" + PLAYER + ";)Z",
                                            PLAYER + ".getX()D",
                                            PLAYER + ".getY()D",
                                            PLAYER + ".getY(D)D",
                                            PLAYER + ".getZ()D",
                                            PLAYER + ".level()L" + LEVEL + ";",
                                            VEC + ".<init>(DDD)V",
                                            VEC + ".vectorTo(L" + VEC + ";)L" + VEC + ";",
                                            VEC + ".normalize()L" + VEC + ";",
                                            VEC + ".scale(D)L" + VEC + ";",
                                            VEC + ".add(DDD)L" + VEC + ";",
                                            "net/minecraft/world/phys/AABB.<init>(DDDDDD)V",
                                            LEVEL + ".getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                                            "java/util/List.iterator()Ljava/util/Iterator;",
                                            "java/util/Iterator.hasNext()Z",
                                            "java/util/Iterator.next()Ljava/lang/Object;",
                                            EFFECT + ".canMagneticPullItem(L" + ITEM + ";)Z",
                                            ITEM + ".getDismountLocationForPassenger(Lnet/minecraft/world/entity/LivingEntity;)L" + VEC + ";",
                                            ITEM + ".getX()D",
                                            ITEM + ".getY()D",
                                            ITEM + ".getZ()D",
                                            TRACKER + ".pushItem" + PUSH),
                        EFFECT_FIELDS, "(L" + PLAYER + ";FL" + ITEM + ";)Z")
                && leaf(owner, "isCrouchDisabled", "(L" + PLAYER + ";)Z", Set.of(PLAYER + ".isCrouching()Z",
                                                                                 "net/neoforged/neoforge/common/ModConfigSpec$BooleanValue.get()Ljava/lang/Object;",
                                                                                 "java/lang/Boolean.booleanValue()Z"))
                && leaf(owner, "canAffectItem", "(L" + STACK + ";)Z", Set.of("java/util/Optional.isEmpty()Z",
                                                                             "java/util/Optional.get()Ljava/lang/Object;",
                                                                             "net/minecraft/world/item/crafting/Ingredient.test(L" + STACK + ";)Z"))
                && leaf(owner, "canMagneticPullItem", "(L" + ITEM + ";)Z", Set.of(ITEM + ".hasPickUpDelay()Z",
                                                                                  ITEM + ".getPersistentData()Lnet/minecraft/nbt/CompoundTag;",
                                                                                  "net/minecraft/nbt/CompoundTag.getBoolean(Ljava/lang/String;)Ljava/util/Optional;",
                                                                                  "java/lang/Boolean.valueOf(Z)Ljava/lang/Boolean;",
                                                                                  "java/util/Optional.orElse(Ljava/lang/Object;)Ljava/lang/Object;",
                                                                                  "java/lang/Boolean.booleanValue()Z",
                                                                                  ITEM + ".getItem()L" + STACK + ";",
                                                                                  EFFECT + ".canAffectItem(L" + STACK + ";)Z"));
    }

    /** Keep the finite-data check aligned with the native radius producer, not a second radius implementation. */
    static boolean radius(MethodNode method) {
        var code = code(method);
        int slot = -1, matches = 0;
        for (int i = 7; i < code.size(); i++) {
            if (!(code.get(i) instanceof VarInsnNode store) || store.getOpcode() != FSTORE) {
                continue;
            }
            if (!load(code.get(i - 7), 0) || !(code.get(i - 6) instanceof FieldInsnNode field)
                    || field.getOpcode() != GETFIELD || !key(field).equals(EFFECT + ".effectRange:F")
                    || !(code.get(i - 5) instanceof VarInsnNode level) || level.getOpcode() != ILOAD || level.var != 2
                    || code.get(i - 4).getOpcode() != I2F || code.get(i - 3).getOpcode() != FMUL
                    || code.get(i - 2).getOpcode() != FCONST_1 || code.get(i - 1).getOpcode() != FADD) {
                return false;
            }
            slot = store.var;
            matches++;
        }
        final int radius = slot;
        return matches == 1 && code.stream()
                                   .filter(node -> node instanceof VarInsnNode store && store.var == radius && store.getOpcode() == FSTORE)
                                   .count() == 1;
    }

    private static boolean leaf(ClassNode owner, String name, String descriptor, Set<String> calls) {
        var method = method(owner, name, descriptor);
        return method != null && body(owner, method, calls, EFFECT_FIELDS, null);
    }

    static boolean tracker(ClassNode owner) {
        var push = method(owner, "pushItem", PUSH);
        var flush = method(owner, "onLevelTick", "(Lnet/neoforged/neoforge/event/tick/LevelTickEvent$Post;)V");
        var fields = Set.of(QUEUE, VEC + ".ZERO:L" + VEC + ";", VEC + ".x:D", VEC + ".y:D", VEC + ".z:D");
        return push != null && flush != null && queue(owner) && strongest(push)
                && body(owner,
                        push,
                        Set.of("java/util/Map.getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                               VEC + ".lengthSqr()D", "java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                        fields,
                        null)
                && drains(flush)
                && body(owner, flush, Set.of("java/util/Map.forEach(Ljava/util/function/BiConsumer;)V", "java/util/Map.clear()V"), fields, PUSH);
    }

    static boolean drains(MethodNode method) {
        var code = code(method);
        int matches = 0;
        for (int i = 2; i + 2 < code.size(); i++) {
            if (!call(code.get(i), "java/util/Map.forEach(Ljava/util/function/BiConsumer;)V")) {
                continue;
            }
            if (!(code.get(i - 2) instanceof FieldInsnNode source) || source.getOpcode() != GETSTATIC || !key(source).equals(QUEUE)
                    || !(code.get(i - 1) instanceof InvokeDynamicInsnNode)
                    || !(code.get(i + 1) instanceof FieldInsnNode target) || target.getOpcode() != GETSTATIC || !key(target).equals(QUEUE)
                    || !call(code.get(i + 2), "java/util/Map.clear()V")) {
                return false;
            }
            matches++;
        }
        return matches == 1 && calls(method, "java/util/Map.clear()V") == 1;
    }

    /** The queue has its own private final producer; unrelated static initialization is not fingerprinted. */
    private static boolean queue(ClassNode owner) {
        if (owner.fields.stream().filter(field -> field.name.equals("ITEMS_TO_PUSH") && field.desc.equals("Ljava/util/Map;")
                && (field.access & (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)) == (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)).count() != 1) {
            return false;
        }
        var init = method(owner, "<clinit>", "()V");
        if (init == null) {
            return false;
        }
        var code = code(init);
        int producers = 0;
        for (int i = 3; i < code.size(); i++) {
            if (!(code.get(i) instanceof FieldInsnNode field) || field.getOpcode() != PUTSTATIC || !key(field).equals(QUEUE)) {
                continue;
            }
            if (!(code.get(i - 3) instanceof TypeInsnNode type) || type.getOpcode() != NEW || !type.desc.equals(
                    "java/util/concurrent/ConcurrentHashMap")
                    || code.get(i - 2).getOpcode() != DUP || !call(code.get(i - 1), "java/util/concurrent/ConcurrentHashMap.<init>()V")) {
                return false;
            }
            producers++;
        }
        return producers == 1;
    }

    /** Require incoming > queued magnitude around the single native queue write, without hashing the method. */
    static boolean strongest(MethodNode method) {
        var code = code(method);
        int found = 0, previous = -1;
        for (int i = 0; i + 2 < code.size(); i++) {
            if (call(code.get(i), "java/util/Map.getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                    && code.get(i + 1) instanceof TypeInsnNode type && type.desc.equals(VEC)
                    && code.get(i + 2) instanceof VarInsnNode store && store.getOpcode() == ASTORE) {
                previous = store.var;
            }
        }
        for (int i = 9; i < code.size(); i++) {
            if (!call(code.get(i), "java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                continue;
            }
            if (!load(code.get(i - 9), 1) || !call(code.get(i - 8), VEC + ".lengthSqr()D")
                    || !load(code.get(i - 7), previous) || !call(code.get(i - 6), VEC + ".lengthSqr()D")
                    || code.get(i - 5).getOpcode() != DCMPL || !(code.get(i - 4) instanceof JumpInsnNode branch) || branch.getOpcode() != IFLE
                    || method.instructions.indexOf(branch.label) <= method.instructions.indexOf(code.get(i))
                    || !(code.get(i - 3) instanceof FieldInsnNode field) || field.getOpcode() != GETSTATIC || !key(field).equals(QUEUE)
                    || !load(code.get(i - 2), 0) || !load(code.get(i - 1), 1)) {
                return false;
            }
            found++;
        }
        return found == 1 && calls(method, "java/util/Map.getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") == 1;
    }

    static boolean body(ClassNode owner, MethodNode method, Set<String> allowed, Set<String> fields, String lambdaDescriptor) {
        for (var node : method.instructions) {
            int opcode = node.getOpcode();
            if (opcode == PUTFIELD || opcode == PUTSTATIC || opcode >= IASTORE && opcode <= SASTORE || opcode == JSR || opcode == RET) {
                return false;
            }
            if (node instanceof FieldInsnNode field && !fields.contains(key(field))) {
                return false;
            }
            if (node instanceof MethodInsnNode call && !allowed.contains(call.owner + "." + call.name + call.desc)) {
                return false;
            }
            if (node instanceof TypeInsnNode type && opcode == NEW && !Set.of(VEC, "net/minecraft/world/phys/AABB").contains(type.desc)) {
                return false;
            }
            if (node instanceof LdcInsnNode ldc && (ldc.cst instanceof ConstantDynamic || ldc.cst instanceof Handle)) {
                return false;
            }
            if (node instanceof InvokeDynamicInsnNode dynamic && !lambda(owner, dynamic, lambdaDescriptor, fields)) {
                return false;
            }
        }
        return true;
    }

    private static boolean lambda(ClassNode owner, InvokeDynamicInsnNode dynamic, String descriptor, Set<String> fields) {
        if (descriptor == null || dynamic.bsm.getTag() != H_INVOKESTATIC || !dynamic.bsm.getDesc().equals(META)
                || !dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !dynamic.bsm.getName().equals("metafactory") || dynamic.bsmArgs.length != 3
                || !(dynamic.bsmArgs[1] instanceof Handle target) || target.getTag() != H_INVOKESTATIC
                || !target.getOwner().equals(owner.name) || !target.getDesc().equals(descriptor)) {
            return false;
        }
        boolean predicate = owner.name.equals(EFFECT);
        if (!dynamic.name.equals(predicate ? "test" : "accept")
                || !dynamic.desc.equals(predicate ? "(L" + PLAYER + ";F)Ljava/util/function/Predicate;" : "()Ljava/util/function/BiConsumer;")
                || !org.objectweb.asm.Type.getMethodType(predicate ? "(Ljava/lang/Object;)Z" : "(Ljava/lang/Object;Ljava/lang/Object;)V")
                                          .equals(dynamic.bsmArgs[0])
                || !org.objectweb.asm.Type.getMethodType(predicate ? "(L" + ITEM + ";)Z" : PUSH).equals(dynamic.bsmArgs[2])) {
            return false;
        }
        var method = method(owner, target.getName(), descriptor);
        if (method == null || (method.access & ACC_STATIC) == 0) {
            return false;
        }
        var permitted = owner.name.equals(EFFECT) ? Set.of(ITEM + ".distanceToSqr(Lnet/minecraft/world/entity/Entity;)D")
                                                  : Set.of(ITEM + ".push(DDD)V");
        return body(owner, method, permitted, fields, null) && calls(method, permitted.iterator().next()) == 1;
    }

    private static boolean forward(MethodNode method) {
        for (var node : method.instructions) {
            if (node instanceof JumpInsnNode jump && method.instructions.indexOf(jump.label) <= method.instructions.indexOf(node)) {
                return false;
            }
        }
        return method.tryCatchBlocks.isEmpty();
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var matches = owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static long calls(MethodNode method, String key) {
        return code(method).stream().filter(node -> call(node, key)).count();
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(node -> node.getOpcode() >= 0 && node.getOpcode() != NOP).toList();
    }

    private static boolean call(AbstractInsnNode node, String key) {
        return node instanceof MethodInsnNode call && (call.owner + "." + call.name + call.desc).equals(key);
    }

    private static String key(FieldInsnNode field) {
        return field.owner + "." + field.name + ":" + field.desc;
    }

    private static boolean load(AbstractInsnNode node, int slot) {
        return node instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == slot;
    }
}
