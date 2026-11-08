package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Proves an additive native count, rather than adding a piece after an unknown transformed result. */
final class AetherSetShape implements Opcodes {
    static final String EQUIPMENT = "com/aetherteam/aetherii/item/equipment/EquipmentUtil";
    static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String TAG = "net/minecraft/tags/TagKey";
    static final String ADAPTER = "games/alejandrocoria/mismatchedleggings/compatibility/aether/AetherSetRecognition";
    static final String COUNT = "(L" + ENTITY + ";L" + TAG + ";)I";
    static final String EQUIPPED = "(L" + ENTITY + ";)Ljava/util/List;";

    private AetherSetShape() {}

    static boolean apply(ClassNode owner) throws AnalyzerException {
        if (!owner.name.equals(EQUIPMENT)) {
            return false;
        }
        var count = method(owner, "getArmorCount", COUNT);
        var equipment = method(owner, "getEquipment", EQUIPPED);
        if (count == null || equipment == null || (count.access & ACC_STATIC) == 0
                || Arrays.stream(count.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode c && c.owner.equals(ADAPTER))
                || !enumeratesLegs(owner, equipment)) {
            return false;
        }

        var increments = Arrays.stream(count.instructions.toArray()).filter(IincInsnNode.class::isInstance)
                .map(IincInsnNode.class::cast).toList();
        if (increments.size() != 1 || increments.getFirst().incr != 1) {
            return false;
        }
        var increment = increments.getFirst();
        var flow = new AetherFlow(owner, count);
        int seeds = 0;
        for (var n : count.instructions) {
            if (n instanceof VarInsnNode store && store.getOpcode() == ISTORE && store.var == increment.var) {
                var value = flow.origin(flow.argument(store, 0, 1));
                if (value == null || value.getOpcode() != ICONST_0) {
                    return false;
                }
                seeds++;
            }
        }
        if (seeds != 1) {
            return false;
        }

        var branches = Arrays.stream(count.instructions.toArray()).filter(JumpInsnNode.class::isInstance)
                .map(JumpInsnNode.class::cast).filter(branch -> branch.getOpcode() != GOTO).toList();
        if (branches.size() != 2) {
            return false;
        }
        var predicate = branches.stream().filter(branch -> next(branch) == increment && next(branch.label) == next(increment))
                .findFirst().orElse(null);
        if (predicate == null || !setPredicate(flow, predicate)) {
            return false;
        }
        var loop = branches.stream().filter(branch -> branch != predicate).findFirst().orElseThrow();
        var hasNext = flow.origin(flow.argument(loop, 0, 1));
        if (loop.getOpcode() != IFEQ || !call(hasNext, "java/util/Iterator", "hasNext", "()Z")
                || !equipmentIterator(flow, flow.argument(hasNext, 0, 1))) {
            return false;
        }

        var returns = Arrays.stream(count.instructions.toArray()).filter(n -> n.getOpcode() == IRETURN).toList();
        if (returns.size() != 1 || !counterResult(flow, flow.argument(returns.getFirst(), 0, 1), increment.var, new HashSet<>())) {
            return false;
        }
        // The raw per-piece comparison remains available to independently optional sibling edits.
        var extension = new InsnList();
        extension.add(new VarInsnNode(ALOAD, 0));
        extension.add(new VarInsnNode(ALOAD, 1));
        extension.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "count", COUNT.replace("(L", "(IL"), false));
        count.instructions.insertBefore(returns.getFirst(), extension);
        count.maxStack += 2;
        return true;
    }

    private static boolean setPredicate(AetherFlow flow, JumpInsnNode branch) throws AnalyzerException {
        SourceValue component;
        SourceValue requested;
        SourceValue peerStack = null;
        if (branch.getOpcode() == IF_ACMPNE) {
            component = flow.argument(branch, 0, 2);
            requested = flow.argument(branch, 1, 2);
        } else if (branch.getOpcode() == IFEQ) {
            var peer = flow.origin(flow.argument(branch, 0, 1));
            if (!call(peer, "games/alejandrocoria/mismatchedboots/compatibility/aether/AetherRecognition", "matchesSet",
                    "(Ljava/lang/Object;L" + TAG + ";L" + STACK + ";)Z")) {
                return false;
            }
            component = flow.argument(peer, 0, 3);
            requested = flow.argument(peer, 1, 3);
            peerStack = flow.argument(peer, 2, 3);
        } else {
            return false;
        }
        var query = flow.origin(component);
        if (!flow.parameter(requested, 1) || !call(query, STACK, "get", "(Ljava/util/function/Supplier;)Ljava/lang/Object;")
                || !field(flow.origin(flow.argument(query, 1, 2)), "com/aetherteam/aetherii/item/components/AetherIIDataComponents",
                        "ARMOR_SET")) {
            return false;
        }
        var receiver = flow.argument(query, 0, 2);
        var item = flow.origin(receiver);
        return (peerStack == null || flow.same(peerStack, receiver)) && call(item, "java/util/Iterator", "next", "()Ljava/lang/Object;")
                && equipmentIterator(flow, flow.argument(item, 0, 1));
    }

    private static boolean equipmentIterator(AetherFlow flow, SourceValue value) {
        var iterator = flow.origin(value);
        if (!call(iterator, "java/util/List", "iterator", "()Ljava/util/Iterator;")) {
            return false;
        }
        var list = flow.origin(flow.argument(iterator, 0, 1));
        return call(list, EQUIPMENT, "getEquipment", EQUIPPED) && flow.parameter(flow.argument(list, 0, 1), 0);
    }

    private static boolean counterResult(AetherFlow flow, SourceValue value, int counter, Set<AbstractInsnNode> seen) throws AnalyzerException {
        if (value == null || value.insns.size() != 1) {
            return false;
        }
        var source = value.insns.iterator().next();
        if (!seen.add(source)) {
            return false;
        }
        if (source instanceof VarInsnNode local) {
            if (local.getOpcode() == ILOAD) {
                return local.var == counter || counterResult(flow, flow.at(local).getLocal(local.var), counter, seen);
            }
            return local.getOpcode() == ISTORE && counterResult(flow, flow.argument(local, 0, 1), counter, seen);
        }
        if (source instanceof MethodInsnNode peer && peer.getOpcode() == INVOKESTATIC && peer.owner.equals(flow.owner.name)
                && peer.desc.startsWith("(I") && peer.desc.endsWith(")I")) {
            var body = method(flow.owner, peer.name, peer.desc);
            if (body == null || body == flow.method) {
                return false;
            }
            var proof = new AetherFlow(flow.owner, body);
            var returns = Arrays.stream(body.instructions.toArray()).filter(n -> n.getOpcode() == IRETURN).toList();
            if (returns.isEmpty() || returns.stream().anyMatch(ret -> !proof.parameter(proof.argument(ret, 0, 1), 0))) {
                return false;
            }
            return counterResult(flow, flow.argument(peer, 0, org.objectweb.asm.Type.getArgumentTypes(peer.desc).length), counter, seen);
        }
        return false;
    }

    private static boolean enumeratesLegs(ClassNode owner, MethodNode method) throws AnalyzerException {
        if ((method.access & ACC_STATIC) == 0) {
            return false;
        }
        var flow = new AetherFlow(owner, method);
        var reads = Arrays.stream(method.instructions.toArray()).filter(n -> call(n, ENTITY, "getItemBySlot",
                "(L" + SLOT + ";)L" + STACK + ";")).toList();
        if (reads.size() != 1) {
            return false;
        }
        var read = reads.getFirst();
        var slot = flow.argument(read, 1, 2);
        var next = flow.origin(slot);
        if (!flow.parameter(flow.argument(read, 0, 2), 0) || !call(next, "java/util/Iterator", "next", "()Ljava/lang/Object;")) {
            return false;
        }
        var iterator = flow.origin(flow.argument(next, 0, 1));
        if (!call(iterator, "net/minecraft/world/entity/EquipmentSlotGroup", "iterator", "()Ljava/util/Iterator;")
                || !field(flow.origin(flow.argument(iterator, 0, 1)), "net/minecraft/world/entity/EquipmentSlotGroup", "ARMOR")) {
            return false;
        }
        var additions = Arrays.stream(method.instructions.toArray()).filter(n -> call(n, "java/util/List", "add", "(Ljava/lang/Object;)Z")).toList();
        if (additions.size() != 1 || flow.origin(flow.argument(additions.getFirst(), 1, 2)) != read) {
            return false;
        }
        var list = flow.argument(additions.getFirst(), 0, 2);
        var returns = Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() == ARETURN).toList();
        if (returns.size() != 1 || !flow.same(list, flow.argument(returns.getFirst(), 0, 1))) {
            return false;
        }
        // Audit the enumeration's controlling predicates, not unrelated native set effect bodies.
        var branches = Arrays.stream(method.instructions.toArray()).filter(JumpInsnNode.class::isInstance)
                .map(JumpInsnNode.class::cast).filter(branch -> branch.getOpcode() != GOTO).toList();
        if (branches.size() != 2) {
            return false;
        }
        boolean type = false;
        boolean loop = false;
        for (var branch : branches) {
            if (branch.getOpcode() == IF_ACMPNE) {
                var getType = flow.origin(flow.argument(branch, 0, 2));
                type = call(getType, SLOT, "getType", "()L" + SLOT + "$Type;")
                        && flow.same(slot, flow.argument(getType, 0, 1))
                        && field(flow.origin(flow.argument(branch, 1, 2)), SLOT + "$Type", "HUMANOID_ARMOR")
                        && before(method, branch, read) && before(method, additions.getFirst(), branch.label);
            } else if (branch.getOpcode() == IFEQ) {
                var hasNext = flow.origin(flow.argument(branch, 0, 1));
                loop = call(hasNext, "java/util/Iterator", "hasNext", "()Z")
                        && flow.same(flow.argument(hasNext, 0, 1), flow.argument(next, 0, 1))
                        && before(method, branch, read) && before(method, additions.getFirst(), branch.label);
            }
        }
        if (!type || !loop) {
            return false;
        }
        // The returned list must not be filtered/replaced after admitting humanoid armor.
        for (var n : method.instructions) {
            if (n instanceof MethodInsnNode call && call.owner.equals("java/util/List") && !call.name.equals("add")) {
                return false;
            }
            if (n instanceof MethodInsnNode call) {
                int arguments = org.objectweb.asm.Type.getArgumentTypes(call.desc).length + (call.getOpcode() == INVOKESTATIC ? 0 : 1);
                for (int argument = 0; argument < arguments; argument++) {
                    if (flow.same(list, flow.argument(call, argument, arguments))
                            && !(call.owner.equals("java/util/List") && call.name.equals("add"))
                            && !(call.owner.equals("java/util/ArrayList") && call.name.equals("<init>") && call.desc.equals("()V"))
                            && !(call.owner.equals("java/util/Objects") && call.name.equals("requireNonNull"))) {
                        return false;
                    }
                }
            } else if (n instanceof InvokeDynamicInsnNode dynamic) {
                int arguments = org.objectweb.asm.Type.getArgumentTypes(dynamic.desc).length;
                for (int argument = 0; argument < arguments; argument++) {
                    if (flow.same(list, flow.argument(dynamic, argument, arguments))
                            && Arrays.stream(dynamic.bsmArgs).noneMatch(value -> value instanceof Handle handle
                                    && handle.getOwner().equals("java/util/List") && handle.getName().equals("add")
                                    && handle.getDesc().equals("(Ljava/lang/Object;)Z"))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean before(MethodNode method, AbstractInsnNode first, AbstractInsnNode second) {
        return method.instructions.indexOf(first) < method.instructions.indexOf(second);
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> methods = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static boolean field(AbstractInsnNode node, String owner, String name) {
        return node instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC && field.owner.equals(owner) && field.name.equals(name);
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
}
