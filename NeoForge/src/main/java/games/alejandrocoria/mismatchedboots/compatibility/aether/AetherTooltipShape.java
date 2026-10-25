package games.alejandrocoria.mismatchedboots.compatibility.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local final-transformed producer arguments; no native effect/helper-body or whole-method fingerprint. */
final class AetherTooltipShape implements Opcodes {
    static final String EVENTS = "com/aetherteam/aetherii/AetherIIEventListeners";
    static final String FORMATTER = "games/alejandrocoria/mismatchedboots/tooltip/MixedBootsTooltipFormatter";
    static final String STATE = "games/alejandrocoria/mismatchedboots/compatibility/aether/AetherTooltipState";
    static final String HOOKS = "com/aetherteam/aetherii/client/event/hooks/RenderHooks";
    static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    static final String CONTEXT = "Lnet/minecraft/world/item/Item$TooltipContext;";
    static final String DISPLAY = "Lnet/minecraft/world/item/component/TooltipDisplay;";
    static final String FLAG = "Lnet/minecraft/world/item/TooltipFlag;";
    static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    static final String CONSUMER = "Ljava/util/function/Consumer;";
    static final String ATTRIBUTE_EVENT = "net/neoforged/neoforge/event/AddAttributeTooltipsEvent";
    static final String TIER_EVENT = "net/neoforged/neoforge/event/entity/player/ItemTooltipEvent";
    static final String ATTRIBUTE_CONTEXT = "Lnet/neoforged/neoforge/common/util/AttributeTooltipContext;";
    static final String DETAILS = "(" + CONTEXT + DISPLAY + PLAYER + FLAG + CONSUMER + ")V";

    private AetherTooltipShape() {
    }

    static boolean producer(ClassNode owner, boolean attributes) throws AnalyzerException {
        String event = attributes ? ATTRIBUTE_EVENT : TIER_EVENT;
        var method = method(owner, attributes ? "onAddAttributeTooltips" : "onAddTooltipsLowest", "(L" + event + ";)V");
        if (method == null || (method.access & ACC_STATIC) == 0 || hooked(method, attributes ? "suppressAttributes" : "suppressTier")) {
            return false;
        }
        String descriptor = attributes ? "(" + STACK + "Ljava/util/List;" + ATTRIBUTE_CONTEXT + ")V"
                                       : "(" + STACK + "Ljava/util/List;" + CONTEXT + FLAG + ")V";
        var calls = List.of(method.instructions.toArray()).stream().filter(n -> call(n, HOOKS,
                                                                                     attributes
                                                                                     ? "addAbilityAttributeTooltip"
                                                                                     : "addReinforcementTooltip", descriptor)).toList();
        if (calls.size() != 1) {
            return false;
        }
        var target = (MethodInsnNode) calls.getFirst();
        var flow = new Flow(owner, method);
        int count = attributes ? 3 : 4;
        if (target.getOpcode() != INVOKESTATIC
                || !flow.eventGetter(flow.argument(target, 0, count), event, attributes ? "getStack" : "getItemStack", STACK)
                || !flow.eventGetter(flow.argument(target, 2, count), event, "getContext", attributes ? ATTRIBUTE_CONTEXT : CONTEXT)
                || !attributes && (!flow.eventGetter(flow.argument(target, 1, count), event, "getToolTip", "Ljava/util/List;")
                || !flow.eventGetter(flow.argument(target, 3, count), event, "getFlags", FLAG))) {
            return false;
        }
        int first = method.maxLocals;
        method.maxLocals += count;
        InsnList condition = new InsnList();
        // Keep the exact final peer arguments. Do not re-query equipment/event getters after wrappers ran.
        for (int i = count - 1; i >= 0; i--) {
            condition.add(new VarInsnNode(ASTORE, first + i));
        }
        condition.add(new VarInsnNode(ALOAD, first));
        condition.add(new MethodInsnNode(INVOKESTATIC, STATE, attributes ? "suppressAttributes" : "suppressTier", "(" + STACK + ")Z", false));
        var after = new LabelNode();
        condition.add(new JumpInsnNode(IFNE, after));
        for (int i = 0; i < count; i++) {
            condition.add(new VarInsnNode(ALOAD, first + i));
        }
        method.instructions.insertBefore(target, condition);
        method.instructions.insert(target, after);
        method.maxStack = Math.max(method.maxStack, count);
        return true;
    }

    static boolean sourceScope(ClassNode owner) throws AnalyzerException {
        var method = method(owner, "appendSourceDetails", "(" + STACK + CONTEXT + DISPLAY + FLAG + CONSUMER + ")V");
        if (method == null || (method.access & ACC_STATIC) == 0 || hooked(method, "details")) {
            return false;
        }
        var flow = new Flow(owner, method);
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) {
            if (!call(instruction, "net/minecraft/world/item/ItemStack", "addDetailsToTooltip", DETAILS)) {
                continue;
            }
            if (!flow.parameter(flow.argument(instruction, 0, 6), 0)
                    || !flow.parameter(flow.argument(instruction, 1, 6), 1)
                    || !flow.parameter(flow.argument(instruction, 2, 6), 2)
                    || flow.origin(flow.argument(instruction, 3, 6)) == null
                    || flow.origin(flow.argument(instruction, 3, 6)).getOpcode() != ACONST_NULL
                    || !flow.parameter(flow.argument(instruction, 4, 6), 3)) {
                return false;
            }
            calls.add((MethodInsnNode) instruction);
        }
        if (calls.size() != 2) {
            return false;
        }
        // Both normal and advanced sites must be admitted before editing either one.
        for (var target : calls) {
            method.instructions.set(target, new MethodInsnNode(INVOKESTATIC, STATE, "details",
                                                               "(" + STACK + DETAILS.substring(1), false));
        }
        return true;
    }

    private static boolean hooked(MethodNode method, String hook) {
        for (var n : method.instructions) {
            if (n instanceof MethodInsnNode call && call.owner.equals(STATE) && call.name.equals(hook)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var matches = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static boolean call(AbstractInsnNode n, String owner, String name, String descriptor) {
        return n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(descriptor);
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
            if (n instanceof VarInsnNode load && n.getOpcode() >= ILOAD && n.getOpcode() <= ALOAD) {
                var local = at(n).getLocal(load.var);
                return local.insns.isEmpty() ? n : origin(local, seen);
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

        boolean eventGetter(SourceValue value, String event, String getter, String result) {
            var n = origin(value);
            Set<AbstractInsnNode> seen = new HashSet<>();
            while (n instanceof MethodInsnNode peer && seen.add(n)) {
                if (call(peer, event, getter, "()" + result)) {
                    return parameter(argument(peer, 0, 1), 0);
                }
                Type[] arguments = Type.getArgumentTypes(peer.desc);
                if (peer.getOpcode() != INVOKESTATIC || arguments.length == 0 || !arguments[0].equals(Type.getReturnType(peer.desc))) {
                    return false;
                }
                n = origin(argument(peer, 0, arguments.length));
            }
            return false;
        }
    }
}
