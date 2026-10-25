package games.alejandrocoria.mismatchedboots.compatibility.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Local operand provenance and consumers on the final transformed class, not whole-method fingerprints. */
final class AetherRecognitionShape implements Opcodes {
    static final String EQUIPMENT = "com/aetherteam/aetherii/item/equipment/EquipmentUtil";
    static final String SENTRY = "com/aetherteam/aetherii/item/equipment/armor/abilities/SentryArmor";
    static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/aether/AetherRecognition";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    static final String TAG = "net/minecraft/tags/TagKey";
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/LivingFallEvent";
    static final String COUNT = "(L" + ENTITY + ";L" + TAG + ";)I";
    static final String FALL = "(L" + EVENT + ";)V";

    private AetherRecognitionShape() {
    }

    static boolean apply(ClassNode owner) throws AnalyzerException {
        boolean sets = owner.name.equals(EQUIPMENT);
        if (!sets && !owner.name.equals(SENTRY)) {
            return false;
        }
        MethodNode method = method(owner, sets ? "getArmorCount" : "playerFall", sets ? COUNT : FALL);
        if (method == null || (method.access & ACC_STATIC) == 0
                || Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode call && call.owner.equals(ADAPTER))) {
            return false;
        }
        Flow flow = new Flow(owner, method);
        Site selected = null;
        for (var instruction : method.instructions.toArray()) {
            if (!(instruction instanceof JumpInsnNode branch)) {
                continue;
            }
            Site site = sets ? setSite(flow, branch) : sentrySite(flow, branch);
            if (site == null) {
                continue;
            }
            if (selected != null) {
                return false;
            }
            selected = site;
        }
        if (selected == null) {
            return false;
        }
        InsnList hook = new InsnList();
        if (sets) {
            hook.add(new VarInsnNode(ALOAD, selected.stackLocal));
            hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "matchesSet", "(Ljava/lang/Object;L" + TAG + ";L" + STACK + ";)Z", false));
            method.instructions.insertBefore(selected.branch, hook);
            method.instructions.set(selected.branch, new JumpInsnNode(IFEQ, selected.branch.label));
        } else {
            // Save the exact original receiver, not another equipment read after peer result wrappers.
            int stackLocal = method.maxLocals++;
            InsnList save = new InsnList();
            save.add(new InsnNode(DUP2));
            save.add(new InsnNode(POP));
            save.add(new VarInsnNode(ASTORE, stackLocal));
            method.instructions.insertBefore(selected.query, save);
            hook.add(new VarInsnNode(ALOAD, stackLocal));
            hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "matchesSentry", "(ZL" + STACK + ";)Z", false));
            method.instructions.insertBefore(selected.branch, hook);
        }
        method.maxStack += 3;
        return true;
    }

    private static Site setSite(Flow flow, JumpInsnNode branch) {
        if (branch.getOpcode() != IF_ACMPNE || !flow.parameter(flow.argument(branch, 1, 2), 1)) {
            return null;
        }
        MethodInsnNode query = flow.query(flow.argument(branch, 0, 2), STACK, "get", "(Ljava/util/function/Supplier;)Ljava/lang/Object;");
        if (query == null || !field(flow.origin(flow.argument(query, 1, 2)),
                                    "com/aetherteam/aetherii/item/components/AetherIIDataComponents",
                                    "ARMOR_SET",
                                    "Lnet/neoforged/neoforge/registries/DeferredHolder;")) {
            return null;
        }
        SourceValue stack = flow.argument(query, 0, 2);
        AbstractInsnNode producer = flow.origin(stack);
        if (!call(producer, "java/util/Iterator", "next", "()Ljava/lang/Object;")) {
            return null;
        }
        AbstractInsnNode iterator = flow.origin(flow.argument(producer, 0, 1));
        if (!call(iterator, "java/util/List", "iterator", "()Ljava/util/Iterator;")) {
            return null;
        }
        AbstractInsnNode equipment = flow.origin(flow.argument(iterator, 0, 1));
        if (!call(equipment, EQUIPMENT, "getEquipment", "(L" + ENTITY + ";)Ljava/util/List;")
                || !flow.parameter(flow.argument(equipment, 0, 1), 0)) {
            return null;
        }
        if (!(next(branch) instanceof IincInsnNode increment) || increment.incr != 1
                || next(branch.label) != next(increment)) {
            return null;
        }
        // Only this comparison's one-piece increment is ours to admit. Native count seeds/return wrappers are not.
        int local = flow.local(branch, stack);
        return local < 0 ? null : new Site(branch, query, local);
    }

    private static Site sentrySite(Flow flow, JumpInsnNode branch) {
        if (branch.getOpcode() != IFEQ) {
            return null;
        }
        MethodInsnNode query = flow.query(flow.argument(branch, 0, 1), STACK, "is", "(Lnet/minecraft/core/Holder;)Z");
        if (query == null || !field(flow.origin(flow.argument(query, 1, 2)), "com/aetherteam/aetherii/item/AetherIIItems",
                                    "SENTRY_BOOTS", "Lnet/neoforged/neoforge/registries/DeferredItem;")) {
            return null;
        }
        AbstractInsnNode feet = flow.origin(flow.argument(query, 0, 2));
        if (!call(feet, ENTITY, "getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)L" + STACK + ";")
                || !field(flow.origin(flow.argument(feet, 1, 2)), "net/minecraft/world/entity/EquipmentSlot", "FEET",
                          "Lnet/minecraft/world/entity/EquipmentSlot;")) {
            return null;
        }
        AbstractInsnNode actor = flow.origin(flow.argument(feet, 0, 2));
        if (!call(actor, EVENT, "getEntity", "()L" + ENTITY + ";") || !flow.parameter(flow.argument(actor, 0, 1), 0)) {
            return null;
        }
        // Require the boolean's true lane to reach the native distance check, without freezing its numeric threshold.
        AbstractInsnNode actorRead = next(branch), distance = actorRead == null ? null : next(actorRead);
        if (!fieldValue(distance, ENTITY, "fallDistance", "D") || !flow.same(flow.argument(distance, 0, 1), flow.argument(feet, 0, 2))) {
            return null;
        }
        return new Site(branch, query, -1);
    }

    private record Site(JumpInsnNode branch, MethodInsnNode query, int stackLocal) {
    }

    private static final class Flow {
        private final MethodNode method;
        private final Frame<SourceValue>[] frames;

        Flow(ClassNode owner, MethodNode method) throws AnalyzerException {
            this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override
                public SourceValue copyOperation(AbstractInsnNode n, SourceValue value) {
                    return n.getOpcode() >= DUP && n.getOpcode() <= SWAP ? value : super.copyOperation(n, value);
                }
            }).analyzeAndComputeMaxs(owner.name, method);
        }

        Frame<SourceValue> at(AbstractInsnNode n) {
            return frames[method.instructions.indexOf(n)];
        }

        SourceValue argument(AbstractInsnNode n, int index, int count) {
            var frame = at(n);
            return frame == null || frame.getStackSize() < count ? null : frame.getStack(frame.getStackSize() - count + index);
        }

        AbstractInsnNode origin(SourceValue value) {
            return origin(value, new HashSet<>());
        }

        private AbstractInsnNode origin(SourceValue value, Set<AbstractInsnNode> seen) {
            if (value == null || value.insns.size() != 1) {
                return null;
            }
            var n = value.insns.iterator().next();
            if (!seen.add(n)) {
                return null;
            }
            if (n instanceof VarInsnNode v && n.getOpcode() >= ILOAD && n.getOpcode() <= ALOAD) {
                var old = at(n).getLocal(v.var);
                return old.insns.isEmpty() ? n : origin(old, seen);
            }
            if (n instanceof VarInsnNode && n.getOpcode() >= ISTORE && n.getOpcode() <= ASTORE || n.getOpcode() == CHECKCAST) {
                return origin(argument(n, 0, 1), seen);
            }
            return n;
        }

        boolean parameter(SourceValue value, int local) {
            var n = origin(value);
            return n instanceof VarInsnNode load && load.var == local && at(n).getLocal(local).insns.isEmpty();
        }

        boolean same(SourceValue a, SourceValue b) {
            var source = origin(a);
            return source != null && source == origin(b);
        }

        int local(AbstractInsnNode at, SourceValue value) {
            var frame = at(at);
            if (frame == null) {
                return -1;
            }
            for (int i = 0; i < frame.getLocals(); i++) {
                if (same(frame.getLocal(i), value)) {
                    return i;
                }
            }
            return -1;
        }

        MethodInsnNode query(SourceValue value, String owner, String name, String descriptor) {
            Set<AbstractInsnNode> seen = new HashSet<>();
            AbstractInsnNode n = origin(value);
            while (n instanceof MethodInsnNode peer && seen.add(n)) {
                if (call(peer, owner, name, descriptor)) {
                    return peer;
                }
                // Admit local expression-result chains, preserving their final value. Opaque replacements are not proof.
                Type[] arguments = Type.getArgumentTypes(peer.desc);
                Type result = Type.getReturnType(peer.desc);
                if (peer.getOpcode() != INVOKESTATIC || arguments.length == 0 || !arguments[0].equals(result)) {
                    return null;
                }
                n = origin(argument(peer, 0, arguments.length));
            }
            return null;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var found = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static boolean call(AbstractInsnNode n, String owner, String name, String descriptor) {
        return n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(descriptor);
    }

    private static boolean field(AbstractInsnNode n, String owner, String name, String descriptor) {
        return n != null && n.getOpcode() == GETSTATIC && fieldValue(n, owner, name, descriptor);
    }

    private static boolean fieldValue(AbstractInsnNode n, String owner, String name, String descriptor) {
        return n instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(descriptor);
    }

    private static AbstractInsnNode next(AbstractInsnNode n) {
        do {
            n = n.getNext();
        } while (n != null && (n.getOpcode() < 0 || n.getOpcode() == NOP));
        return n;
    }
}
