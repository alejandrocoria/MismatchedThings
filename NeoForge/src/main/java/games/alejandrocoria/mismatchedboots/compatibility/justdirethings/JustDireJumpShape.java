package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local equipment/consumer proof, not a whole LivingEntityEvents fingerprint. */
public final class JustDireJumpShape implements Opcodes {
    public static final String HANDLER = "com/direwolf20/justdirethings/common/events/LivingEntityEvents";
    static final String TOOL = "com/direwolf20/justdirethings/common/items/interfaces/ToggleableTool";
    static final String ABILITY = "com/direwolf20/justdirethings/common/items/interfaces/Ability";
    static final String HELPERS = "com/direwolf20/justdirethings/common/items/interfaces/Helpers";
    static final String ACTION = "com/direwolf20/justdirethings/common/items/interfaces/AbilityMethods";
    static final String PLAYER = "net/minecraft/world/entity/player/Player";
    static final String SERVER_PLAYER = "net/minecraft/server/level/ServerPlayer";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String READ = "(L" + SLOT + ";)L" + STACK + ";";
    static final String JUMP = "(Lnet/neoforged/neoforge/event/entity/living/LivingEvent$LivingJumpEvent;)V";
    static final String FALL = "(Lnet/neoforged/neoforge/event/entity/living/LivingFallEvent;)V";
    private static final String GETTER = "mismatchedboots$justDireJumpReady";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.compatibility.justdirethings.JustDireJumpMixin";
    private static final String ADAPTER = JustDireJumpCompatibility.class.getName().replace('.', '/');

    private JustDireJumpShape() {
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }

    static List<AbstractInsnNode> code(InsnList instructions) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (var node : instructions) {
            if (node.getOpcode() >= 0) {
                result.add(node);
            }
        }
        return result;
    }

    /** The same late selector is used by both injections; relocated locals/debug nodes are irrelevant. */
    static MethodInsnNode feetRead(String desc, InsnList instructions) {
        String receiver = desc.equals(JUMP) ? PLAYER : desc.equals(FALL) ? SERVER_PLAYER : null;
        if (receiver == null) {
            return null;
        }
        MethodInsnNode result = null;
        List<AbstractInsnNode> nodes = code(instructions);
        for (int i = 1; i + 8 < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof FieldInsnNode field) || field.getOpcode() != GETSTATIC
                    || !field.owner.equals(SLOT) || !field.name.equals("FEET") || !field.desc.equals("L" + SLOT + ";")
                    || !(nodes.get(i - 1) instanceof VarInsnNode player) || player.getOpcode() != ALOAD
                    || !typedPlayer(nodes, i - 1, receiver)
                    || !(nodes.get(i + 1) instanceof MethodInsnNode read) || read.getOpcode() != INVOKEVIRTUAL
                    || !read.owner.equals(receiver) || !read.name.equals("getItemBySlot") || !read.desc.equals(READ)
                    || !nativeConsumer(nodes, i + 2)) {
                continue;
            }
            if (result != null) {
                return null;
            }
            result = read;
        }
        // A second FEET read with an incompatible consumer must not hide behind the good one.
        long feet = nodes.stream().filter(n -> n instanceof FieldInsnNode f && f.owner.equals(SLOT) && f.name.equals("FEET")).count();
        return feet == 1 ? result : null;
    }

    private static boolean typedPlayer(List<AbstractInsnNode> nodes, int feetPlayer, String receiver) {
        int variable = ((VarInsnNode) nodes.get(feetPlayer)).var;
        for (int i = feetPlayer - 1; i >= 8; i--) {
            if (!(nodes.get(i) instanceof VarInsnNode store) || store.getOpcode() != ASTORE || store.var != variable) {
                continue;
            }
            // The nearest write must be the player's type-narrowing from this event.
            return nodes.get(i - 1) instanceof TypeInsnNode cast && cast.getOpcode() == CHECKCAST && cast.desc.equals(receiver)
                    && nodes.get(i - 2) instanceof VarInsnNode narrowed && narrowed.getOpcode() == ALOAD
                    && nodes.get(i - 3) instanceof JumpInsnNode branch && branch.getOpcode() == IFEQ
                    && nodes.get(i - 4) instanceof TypeInsnNode check && check.getOpcode() == INSTANCEOF && check.desc.equals(receiver)
                    && nodes.get(i - 5) instanceof VarInsnNode tested && tested.getOpcode() == ALOAD && tested.var == narrowed.var
                    && nodes.get(i - 6) instanceof VarInsnNode entity && entity.getOpcode() == ASTORE && entity.var == narrowed.var
                    && nodes.get(i - 7) instanceof MethodInsnNode getter && getter.name.equals("getEntity")
                    && getter.owner.equals(receiver.equals(PLAYER) ? "net/neoforged/neoforge/event/entity/living/LivingEvent$LivingJumpEvent"
                                                                   : "net/neoforged/neoforge/event/entity/living/LivingFallEvent")
                    && getter.desc.equals("()Lnet/minecraft/world/entity/LivingEntity;")
                    && nodes.get(i - 8) instanceof VarInsnNode event && event.getOpcode() == ALOAD && event.var == 0;
        }
        return false;
    }

    private static boolean nativeConsumer(List<AbstractInsnNode> nodes, int index) {
        if (index + 7 >= nodes.size()
                || !(nodes.get(index) instanceof VarInsnNode store) || store.getOpcode() != ASTORE
                || !(nodes.get(index + 1) instanceof VarInsnNode load) || load.getOpcode() != ALOAD || load.var != store.var
                || !(nodes.get(index + 2) instanceof MethodInsnNode item) || !item.owner.equals(STACK)
                || !item.name.equals("getItem") || !item.desc.equals("()Lnet/minecraft/world/item/Item;")
                || !(nodes.get(index + 3) instanceof VarInsnNode itemStore) || itemStore.getOpcode() != ASTORE
                || !(nodes.get(index + 4) instanceof VarInsnNode itemLoad) || itemLoad.getOpcode() != ALOAD || itemLoad.var != itemStore.var
                || !(nodes.get(index + 5) instanceof TypeInsnNode check) || check.getOpcode() != INSTANCEOF || !check.desc.equals(TOOL)
                || !(nodes.get(index + 6) instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ) {
            return false;
        }
        return nodes.subList(index + 7, nodes.size()).stream().anyMatch(n -> n instanceof FieldInsnNode f
                && f.owner.equals(ABILITY) && f.name.equals("JUMPBOOST"));
    }

    static boolean entries(ClassNode handler) {
        MethodNode jump = method(handler, "jumpEvent", JUMP);
        MethodNode fall = method(handler, "LivingFallDamage", FALL);
        return jump != null && fall != null && jump.access == (ACC_PUBLIC | ACC_STATIC)
                && fall.access == (ACC_PUBLIC | ACC_STATIC) && feetRead(JUMP, jump.instructions) != null
                && feetRead(FALL, fall.instructions) != null;
    }

    static boolean api(ClassNode tool, ClassNode helpers, ClassNode action) {
        return query(tool, "canUseAbility", "(L" + STACK + ";L" + ABILITY + ";)Z",
                     Set.of("hasAbility", "hasUpgrade", "getEnabled", "getSetting", "getName"))
                && query(tool, "getToolValue", "(L" + STACK + ";Ljava/lang/String;)I",
                         Set.of("byName", "getItem", "getAbilityParams", "get", "has", "getOrDefault", "intValue", "valueOf", "min", "max"))
                && query(helpers, "testUseTool", "(L" + STACK + ";L" + ABILITY + ";)I",
                         Set.of("getItem",
                                "getCapability",
                                "forStack",
                                "getAmountAsInt",
                                "getFeCost",
                                "getMaxDamage",
                                "getDamageValue",
                                "getDurabilityCost"))
                && jumpAction(action);
    }

    static boolean query(ClassNode owner, String name, String desc, Set<String> allowedCalls) {
        MethodNode m = method(owner, name, desc);
        if (m == null || (m.access & ACC_PUBLIC) == 0 || (m.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0
                || !m.tryCatchBlocks.isEmpty()) {
            return false;
        }
        for (var n : m.instructions) {
            if (n.getOpcode() == PUTFIELD || n.getOpcode() == PUTSTATIC || n.getOpcode() == ATHROW
                    || n instanceof InvokeDynamicInsnNode || n instanceof MethodInsnNode c
                    && (!allowedCalls.contains(c.name) || !queryOwner(c))) {
                return false;
            }
        }
        return true;
    }

    private static boolean queryOwner(MethodInsnNode call) {
        // Names alone are not a read-only contract: a foreign method named "get" may do work.
        return switch (call.owner) {
            case TOOL -> Set.of("hasAbility", "hasUpgrade", "getEnabled", "getSetting", "getToolValue", "getAbilityParams").contains(call.name);
            case ABILITY -> Set.of("getName", "byName", "getFeCost", "getDurabilityCost").contains(call.name);
            case STACK -> Set.of("getItem", "has", "getOrDefault", "getCapability", "getMaxDamage", "getDamageValue").contains(call.name);
            case "java/lang/Integer" -> Set.of("intValue", "valueOf").contains(call.name);
            case "java/lang/Math" -> Set.of("min", "max").contains(call.name);
            case "java/util/Map" -> call.name.equals("get");
            case PLAYER -> Set.of("isInWater", "isFallFlying", "moveRelative").contains(call.name);
            case "net/minecraft/world/phys/Vec3" -> call.name.equals("<init>");
            case "net/neoforged/neoforge/transfer/access/ItemAccess" -> call.name.equals("forStack");
            case "net/neoforged/neoforge/transfer/energy/EnergyHandler" -> call.name.equals("getAmountAsInt");
            default -> false;
        };
    }

    private static boolean jumpAction(ClassNode owner) {
        MethodNode m = method(owner, "jumpBoost", "(Lnet/minecraft/world/level/Level;L" + PLAYER + ";L" + STACK + ";)Z");
        if (m == null || !query(owner, m.name, m.desc, Set.of("isInWater", "isFallFlying", "getName", "getToolValue", "<init>", "moveRelative"))) {
            return false;
        }
        List<AbstractInsnNode> nodes = code(m.instructions);
        if (nodes.size() != 25 || !(nodes.get(2) instanceof JumpInsnNode water) || water.getOpcode() != IFNE
                || !(nodes.get(5) instanceof JumpInsnNode glide) || glide.getOpcode() != IFNE
                || water.label != glide.label || nextCode(water.label) != nodes.get(23)
                || nodes.get(23).getOpcode() != ICONST_0 || nodes.get(24).getOpcode() != IRETURN) {
            return false;
        }
        return var(nodes, 0, ALOAD, 1) && call(nodes, 1, PLAYER, "isInWater", "()Z")
                && var(nodes, 3, ALOAD, 1) && call(nodes, 4, PLAYER, "isFallFlying", "()Z")
                && var(nodes, 6, ALOAD, 2)
                && nodes.get(7) instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC && field.owner.equals(ABILITY) && field.name.equals(
                "JUMPBOOST")
                && call(nodes, 8, ABILITY, "getName", "()Ljava/lang/String;")
                && call(nodes, 9, TOOL, "getToolValue", "(L" + STACK + ";Ljava/lang/String;)I")
                && nodes.get(10).getOpcode() == I2F && nodes.get(11) instanceof LdcInsnNode constant && Float.valueOf(7.5f).equals(constant.cst)
                && nodes.get(13) instanceof VarInsnNode store && store.getOpcode() == FSTORE
                && var(nodes, 14, ALOAD, 1) && var(nodes, 15, FLOAD, store.var)
                && nodes.get(16) instanceof TypeInsnNode vector && vector.getOpcode() == NEW && vector.desc.equals("net/minecraft/world/phys/Vec3")
                && nodes.get(17).getOpcode() == DUP
                && call(nodes, 21, "net/minecraft/world/phys/Vec3", "<init>", "(DDD)V")
                && call(nodes, 22, PLAYER, "moveRelative", "(FLnet/minecraft/world/phys/Vec3;)V")
                && nodes.get(12).getOpcode() == FDIV && nodes.get(18).getOpcode() == DCONST_0
                && nodes.get(19).getOpcode() == DCONST_1 && nodes.get(20).getOpcode() == DCONST_0;
    }

    private static boolean var(List<AbstractInsnNode> nodes, int i, int opcode, int variable) {
        return nodes.get(i) instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == variable;
    }

    private static boolean call(List<AbstractInsnNode> nodes, int i, String owner, String name, String desc) {
        return nodes.get(i) instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc);
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode n) {
        do {
            n = n.getNext();
        } while (n != null && n.getOpcode() < 0);
        return n;
    }

    /** Final MixinExtras export: require both reachable owned wrappers and their unchanged return stack. */
    static boolean publish(ClassNode handler) {
        MethodNode getter = method(handler, GETTER, "()Z");
        if (getter == null) {
            return false;
        }
        List<AbstractInsnNode> getterCode = code(getter.instructions);
        boolean originalGetter = getter.access == (ACC_PRIVATE | ACC_STATIC) && owned(getter)
                && getter.tryCatchBlocks.isEmpty() && getterCode.size() == 2
                && getterCode.getFirst().getOpcode() == ICONST_0 && getterCode.getLast().getOpcode() == IRETURN;
        // Re-establish denial before checking the final pair, including a competing getter edit.
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
        getter.access = ACC_PRIVATE | ACC_STATIC;
        getter.maxStack = Math.max(getter.maxStack, 1);
        getter.tryCatchBlocks.clear();
        getter.localVariables = null;
        getter.visibleLocalVariableAnnotations = null;
        getter.invisibleLocalVariableAnnotations = null;
        if (!originalGetter) {
            return false;
        }
        if (!installed(handler, "jumpEvent", JUMP, "jump", PLAYER)
                || !installed(handler, "LivingFallDamage", FALL, "fall", SERVER_PLAYER)) {
            return false;
        }
        getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    static boolean installed(ClassNode owner, String entry, String desc, String effect, String receiver) {
        MethodNode m = method(owner, entry, desc);
        if (m == null) {
            return false;
        }
        List<AbstractInsnNode> nodes = code(m.instructions);
        MethodInsnNode dispatch = null;
        List<InvokeDynamicInsnNode> operations = new ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof FieldInsnNode f) || !f.owner.equals(SLOT) || !f.name.equals("FEET")) {
                continue;
            }
            if (dispatch != null || f.getOpcode() != GETSTATIC || !f.desc.equals("L" + SLOT + ";")
                    || !(nodes.get(i - 1) instanceof VarInsnNode p) || p.getOpcode() != ALOAD
                    || !typedPlayer(nodes, i - 1, receiver)) {
                return false;
            }
            int after = i + 1;
            // MixinExtras spills the receiver/slot, then supplies an Operation lambda.
            // Prove that the reloaded arguments are these same two values, not another slot/player.
            if (after + 4 < nodes.size() && nodes.get(after) instanceof VarInsnNode slotStore && slotStore.getOpcode() == ASTORE
                    && nodes.get(after + 1) instanceof VarInsnNode playerStore && playerStore.getOpcode() == ASTORE
                    && nodes.get(after + 2) instanceof VarInsnNode playerLoad && playerLoad.getOpcode() == ALOAD && playerLoad.var == playerStore.var
                    && nodes.get(after + 3) instanceof VarInsnNode slotLoad && slotLoad.getOpcode() == ALOAD && slotLoad.var == slotStore.var
                    && nodes.get(after + 4) instanceof InvokeDynamicInsnNode lambda
                    && lambda.desc.equals("()Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")) {
                operations.add(lambda);
                after += 5;
            }
            // Additional compatible WrapOperations spill/reload this same triple and
            // capture the preceding Operation (plus the enclosing fall event when needed).
            for (int layer = 0; layer < 8 && after + 6 < nodes.size(); layer++) {
                int capturedEvent = -1;
                if (desc.equals(FALL) && nodes.get(after) instanceof VarInsnNode eventLoad && eventLoad.getOpcode() == ALOAD && eventLoad.var == 0
                        && nodes.get(after + 1) instanceof VarInsnNode eventStore && eventStore.getOpcode() == ASTORE) {
                    capturedEvent = eventStore.var;
                    after += 2;
                }
                if (!(nodes.get(after) instanceof VarInsnNode opStore) || opStore.getOpcode() != ASTORE) {
                    break;
                }
                if (!(nodes.get(after + 1) instanceof VarInsnNode slotStore) || slotStore.getOpcode() != ASTORE
                        || !(nodes.get(after + 2) instanceof VarInsnNode playerStore) || playerStore.getOpcode() != ASTORE
                        || !(nodes.get(after + 3) instanceof VarInsnNode playerLoad) || playerLoad.getOpcode() != ALOAD || playerLoad.var != playerStore.var
                        || !(nodes.get(after + 4) instanceof VarInsnNode slotLoad) || slotLoad.getOpcode() != ALOAD || slotLoad.var != slotStore.var
                        || !(nodes.get(after + 5) instanceof VarInsnNode opLoad) || opLoad.getOpcode() != ALOAD || opLoad.var != opStore.var) {
                    return false;
                }
                after += 6;
                if (capturedEvent >= 0) {
                    if (!(nodes.get(after) instanceof VarInsnNode eventLoad) || eventLoad.getOpcode() != ALOAD || eventLoad.var != capturedEvent) {
                        return false;
                    }
                    after++;
                }
                if (after >= nodes.size() || !(nodes.get(after) instanceof InvokeDynamicInsnNode lambda)
                        || !lambda.desc.startsWith("(Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")
                        || !lambda.desc.endsWith(")Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")) {
                    return false;
                }
                operations.add(lambda);
                after++;
            }
            while (after < nodes.size() && nodes.get(after) instanceof VarInsnNode v && v.getOpcode() == ALOAD) {
                after++;
            }
            if (after >= nodes.size() || !(nodes.get(after) instanceof MethodInsnNode c) || !c.owner.equals(HANDLER)
                    || c.getOpcode() != INVOKESTATIC || !c.desc.startsWith("(L" + receiver + ";L" + SLOT + ";")
                    || !c.desc.endsWith(")L" + STACK + ";") || !nativeConsumer(nodes, after + 1)) {
                return false;
            }
            dispatch = c;
        }
        if (dispatch == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        List<MethodNode> reachable = new ArrayList<>();
        if (!walk(owner, dispatch.name, dispatch.desc, visited, reachable)) {
            return false;
        }
        if (operations.isEmpty()) {
            return false;
        }
        for (var operation : operations) {
            int bridges = 0;
            for (Object arg : operation.bsmArgs) {
                if (arg instanceof Handle h && h.getOwner().equals(HANDLER)) {
                    if (!walk(owner, h.getName(), h.getDesc(), visited, reachable)) {
                        return false;
                    }
                    bridges++;
                }
            }
            if (bridges != 1) {
                return false;
            }
        }
        int own = 0;
        int original = 0;
        for (MethodNode method : reachable) {
            for (var n : method.instructions) {
                if (n instanceof MethodInsnNode call && call.owner.equals(receiver) && call.name.equals("getItemBySlot") && call.desc.equals(READ)) {
                    original++;
                }
                if (n instanceof MethodInsnNode call && call.owner.equals(ADAPTER)) {
                    if (!call.name.equals(effect) || !owned(method) || !returnsOriginal(method)) {
                        return false;
                    }
                    own++;
                }
            }
        }
        return own == 1 && original == 1;
    }

    private static boolean returnsOriginal(MethodNode m) {
        List<AbstractInsnNode> nodes = code(m.instructions);
        if (nodes.size() < 4 || !(nodes.get(nodes.size() - 2) instanceof VarInsnNode load) || load.getOpcode() != ALOAD
                || nodes.getLast().getOpcode() != ARETURN) {
            return false;
        }
        boolean stored = false;
        boolean permission = false;
        int calls = 0;
        int stores = 0;
        for (int i = 0; i + 2 < nodes.size(); i++) {
            if (nodes.get(i) instanceof MethodInsnNode c && c.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
                    && c.name.equals("call") && nodes.get(i + 1) instanceof TypeInsnNode cast && cast.desc.equals(STACK)
                    && nodes.get(i + 2) instanceof VarInsnNode store && store.getOpcode() == ASTORE && store.var == load.var) {
                stored = true;
                calls++;
            }
            if (nodes.get(i) instanceof VarInsnNode store && store.getOpcode() == ASTORE && store.var == load.var) {
                stores++;
            }
            if (nodes.get(i) instanceof MethodInsnNode c && c.owner.equals(HANDLER) && c.name.equals(GETTER)
                    && nodes.get(i + 1) instanceof JumpInsnNode branch && branch.getOpcode() == IFEQ
                    && nextCode(branch.label) == nodes.get(nodes.size() - 2)) {
                permission = true;
            }
        }
        return stored && permission && calls == 1 && stores == 1 && m.tryCatchBlocks.isEmpty();
    }

    private static boolean walk(ClassNode owner, String name, String desc, Set<String> seen, List<MethodNode> result) {
        if (!seen.add(name + desc)) {
            return true;
        }
        if (seen.size() > 32) {
            return false;
        }
        MethodNode m = method(owner, name, desc);
        if (m == null) {
            return false;
        }
        result.add(m);
        for (var n : m.instructions) {
            if (n instanceof MethodInsnNode c && c.owner.equals(HANDLER) && !c.name.equals(GETTER)
                    && !walk(owner, c.name, c.desc, seen, result)) {
                return false;
            }
            if (n instanceof InvokeDynamicInsnNode dynamic) {
                for (Object arg : dynamic.bsmArgs) {
                    if (arg instanceof Handle h && h.getOwner().equals(HANDLER)
                            && !walk(owner, h.getName(), h.getDesc(), seen, result)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean owned(MethodNode m) {
        if (m.visibleAnnotations == null) {
            return false;
        }
        return m.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                && a.values != null && a.values.contains(MIXIN));
    }
}
