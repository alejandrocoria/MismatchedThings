package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

/** Reachable call/write boundaries for the selected potion path, not a whole-class fingerprint. */
final class SilentGearWielderShape implements Opcodes {
    static final String OWNER = SilentGearWearerShape.OWNER;
    static final String DATA = OWNER + "$PotionData";
    static final String QUERY = SilentGearEffectShape.PREFIX + "util/TraitHelper";
    private static final String PREFIX = SilentGearEffectShape.PREFIX;
    private static final String CTX = SilentGearEffectShape.CONTEXT;
    private static final String PLAYER = SilentGearWearerShape.PLAYER;
    private static final String STACK = SilentGearEffectShape.STACK;
    private static final String TYPE = PREFIX + "api/item/GearType";

    private SilentGearWielderShape() {
    }

    static void publish(ClassNode owner) {
        if (owner.name.equals(OWNER)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgWielderReady", SilentGearWearerShape.set(owner) && effects(owner));
        } else if (owner.name.equals(DATA)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgPotionReady", potion(owner));
        } else if (owner.name.equals(QUERY)) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgWearerQueryReady", query(owner));
        } else if (owner.name.equals(PREFIX + "util/GearHelper")) {
            SilentGearEffectShape.publish(owner, "mismatchedboots$sgGearIdentityReady", identity(owner));
        }
    }

    static boolean identity(ClassNode owner) {
        var methods = owner.methods.stream().filter(method -> method.name.equals("isGear")
                && method.desc.equals("(Lnet/minecraft/world/item/ItemInstance;)Z")).toList();
        if (methods.size() != 1 || !methods.getFirst().tryCatchBlocks.isEmpty()) {
            return false;
        }

        var code = java.util.Arrays.stream(methods.getFirst().instructions.toArray())
                                   .filter(node -> node.getOpcode() >= 0 && node.getOpcode() != NOP)
                                   .toList();
        // TraitHelper's early exit is safe only if mixed roots remain non-GearItem here.
        return code.size() == 5 && code.get(0) instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == 0
                && code.get(1) instanceof MethodInsnNode holder && holder.owner.equals("net/minecraft/world/item/ItemInstance")
                && holder.name.equals("typeHolder") && holder.desc.equals("()Lnet/minecraft/core/Holder;")
                && code.get(2) instanceof MethodInsnNode value && value.owner.equals("net/minecraft/core/Holder")
                && value.name.equals("value") && value.desc.equals("()Ljava/lang/Object;")
                && code.get(3) instanceof TypeInsnNode check && check.getOpcode() == INSTANCEOF && check.desc.equals(PREFIX + "api/item/GearItem")
                && code.get(4).getOpcode() == IRETURN;
    }

    static boolean effects(ClassNode owner) {
        var counterCalls = new java.util.HashSet<>(Set.of(TYPE + ".matches(L" + TYPE + ";)Z",
                                                          "net/neoforged/neoforge/registries/DeferredHolder.get()Ljava/lang/Object;",
                                                          "net/minecraft/world/entity/EquipmentSlotGroup.iterator()Ljava/util/Iterator;",
                                                          "java/util/Iterator.hasNext()Z",
                                                          "java/util/Iterator.next()Ljava/lang/Object;",
                                                          PLAYER + ".getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)L" + STACK + ";",
                                                          STACK + ".getItem()Lnet/minecraft/world/item/Item;",
                                                          CTX + ".trait()L" + SilentGearWearerShape.TRAIT + ";",
                                                          QUERY + ".hasTrait" + SilentGearWearerShape.HAS));
        for (var method : owner.methods) {
            if (!method.name.equals("getSetPieceCount")) {
                continue;
            }
            for (var node : method.instructions) {
                if (node instanceof MethodInsnNode hook && hook.owner.equals(OWNER)
                        && hook.desc.equals("(ZLjava/lang/Object;Ljava/lang/Object;L" + PLAYER + ";Lnet/minecraft/world/entity/EquipmentSlot;L" + STACK + ";)Z")) {
                    counterCalls.add(hook.owner + "." + hook.name + hook.desc);
                }
            }
        }

        return safe(owner, "getSetPieceCount", counterCalls) && safe(owner, "inventoryTick", Set.of(
                CTX + ".player()L" + PLAYER + ";", CTX + ".gear()L" + STACK + ";",
                STACK + ".getItem()Lnet/minecraft/world/item/Item;",
                PREFIX + "api/item/GearItem.getGearType()L" + TYPE + ";",
                "java/util/Map.forEach(Ljava/util/function/BiConsumer;)V"))
                && safe(owner,
                        "lambda$inventoryTick$0",
                        Set.of(OWNER + ".applyEffects(L" + CTX + ";L" + TYPE + ";L" + TYPE + ";Ljava/lang/Iterable;)V"))
                && safe(owner, "applyEffects", Set.of(
                CTX + ".player()L" + PLAYER + ";", "java/lang/AssertionError.<init>()V",
                TYPE + ".matches(L" + TYPE + ";Z)Z", TYPE + ".matches(L" + TYPE + ";)Z",
                OWNER + ".getSetPieceCount" + SilentGearWearerShape.COUNT,
                "net/neoforged/neoforge/registries/DeferredHolder.get()Ljava/lang/Object;",
                "java/lang/Iterable.iterator()Ljava/util/Iterator;", "java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;",
                CTX + ".traitLevel()I", DATA + ".getEffect(IIZ)Lnet/minecraft/world/effect/MobEffectInstance;",
                PLAYER + ".addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"));
    }

    static boolean potion(ClassNode owner) {
        return safe(owner, "getEffect", Set.of(DATA + ".getEffectLevel(IIZ)I",
                                               "net/minecraft/world/effect/MobEffectInstance.<init>(Lnet/minecraft/core/Holder;IIZZ)V"))
                && safe(owner, "getEffectLevel", Set.of(OWNER + "$LevelType.ordinal()I",
                                                        "java/lang/MatchException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                                                        "java/util/List.size()I",
                                                        "net/minecraft/util/Mth.clamp(III)I",
                                                        "java/util/List.get(I)Ljava/lang/Object;",
                                                        "java/util/List.getFirst()Ljava/lang/Object;",
                                                        "java/lang/Integer.intValue()I"));
    }

    static boolean query(ClassNode owner) {
        var matches = owner.methods.stream()
                                   .filter(method -> method.name.equals("hasTrait") && method.desc.equals(SilentGearWearerShape.HAS))
                                   .toList();
        if (matches.size() != 1 || !matches.getFirst().tryCatchBlocks.isEmpty()) {
            return false;
        }

        var method = matches.getFirst();
        var code = java.util.Arrays.stream(method.instructions.toArray()).filter(node -> node.getOpcode() >= 0 && node.getOpcode() != NOP).toList();
        // Only the non-gear early exit becomes newly reachable for a mixed root. Never enter GearData.
        if (code.size() < 6 || !(code.get(0) instanceof VarInsnNode load) || load.getOpcode() != ALOAD || load.var != 0
                || !(code.get(1) instanceof MethodInsnNode call) || !call.owner.equals(PREFIX + "util/GearHelper")
                || !call.name.equals("isGear") || !call.desc.equals("(Lnet/minecraft/world/item/ItemInstance;)Z")
                || !(code.get(2) instanceof JumpInsnNode skip) || skip.getOpcode() != IFEQ) {
            return false;
        }

        var exit = skip.label.getNext();
        while (exit != null && (exit.getOpcode() < 0 || exit.getOpcode() == NOP)) {
            exit = exit.getNext();
        }
        if (exit == null || exit.getOpcode() != ICONST_0) {
            return false;
        }
        exit = exit.getNext();
        while (exit != null && (exit.getOpcode() < 0 || exit.getOpcode() == NOP)) {
            exit = exit.getNext();
        }

        return exit != null && exit.getOpcode() == IRETURN;
    }

    private static boolean safe(ClassNode owner, String name, Set<String> allowed) {
        var methods = owner.methods.stream().filter(method -> method.name.equals(name)).toList();
        if (methods.size() != 1) {
            return false;
        }

        MethodNode method = methods.getFirst();
        if (!method.tryCatchBlocks.isEmpty()) {
            return false;
        }
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field && (field.getOpcode() == PUTFIELD || field.getOpcode() == PUTSTATIC)
                    || node.getOpcode() >= IASTORE && node.getOpcode() <= SASTORE) {
                return false;
            }
            if (node instanceof MethodInsnNode call && !allowed.contains(call.owner + "." + call.name + call.desc)) {
                return false;
            }
            if (node instanceof InvokeDynamicInsnNode dynamic) {
                if (!name.equals("inventoryTick") || !dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        || dynamic.bsmArgs.length != 3 || !(dynamic.bsmArgs[1] instanceof Handle target)
                        || !target.getOwner().equals(OWNER) || !target.getName().equals("lambda$inventoryTick$0")) {
                    return false;
                }
            }
        }

        return true;
    }
}
