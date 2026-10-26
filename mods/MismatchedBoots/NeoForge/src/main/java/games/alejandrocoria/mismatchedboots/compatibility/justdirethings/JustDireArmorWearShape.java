package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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
import java.util.HashSet;
import java.util.Set;

/** The post/cancellation/map/lambda slice only, independent of unrelated CommonHooks methods. */
public final class JustDireArmorWearShape implements Opcodes {
    static final String OWNER = "net/neoforged/neoforge/common/CommonHooks";
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/ArmorHurtEvent";
    static final String ENTRY = "L" + EVENT + "$ArmorEntry;";
    static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    static final String ENTITY = "Lnet/minecraft/world/entity/LivingEntity;";
    static final String SLOT = "Lnet/minecraft/world/entity/EquipmentSlot;";
    static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    static final String LAMBDA = "lambda$onArmorHurt$0";
    static final String GETTER = "mismatchedboots$justDireArmorWearReady";
    static final String DAMAGE_DESC = "(I" + ENTITY + SLOT + ")V";
    static final String HANDLER_DESC = "(" + STACK + "I" + ENTITY + SLOT + OPERATION + ENTITY + SLOT + ENTRY + ")V";
    private static final String FORWARD_DESC = "(" + STACK + "I" + ENTITY + SLOT + OPERATION + ")V";
    static final String ROUTE_DESC = "(Z" + STACK + "I" + ENTITY + SLOT + OPERATION + "F)V";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.compatibility.justdirethings.JustDireArmorWearMixin";

    private JustDireArmorWearShape() {
    }

    static boolean entry(ClassNode node) {
        try {
            return caller(node) && nativeConsumer(node);
        } catch (AnalyzerException | RuntimeException incompatible) {
            return false;
        }
    }

    static boolean publish(ClassNode node) {
        var getter = method(node, GETTER, "()Z");
        if (getter == null) {
            return refuse(node);
        }

        var code = real(getter);
        boolean intact = owned(getter) && getter.access == (ACC_PRIVATE | ACC_STATIC) && getter.tryCatchBlocks.isEmpty()
                && code.size() == 2 && code.getFirst().getOpcode() == ICONST_0 && code.getLast().getOpcode() == IRETURN;
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
        getter.tryCatchBlocks.clear();
        getter.maxStack = 1;
        if (!intact) {
            return refuse(node);
        }

        try {
            if (!caller(node) || !transformedConsumer(node)) {
                return refuse(node);
            }
        } catch (AnalyzerException | RuntimeException incompatible) {
            return refuse(node);
        }
        getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    private static boolean refuse(ClassNode node) {
        // A foreign edit can bypass the getter (e.g. constant true). Neutralize only OUR router,
        // not foreign methods: keep its supplied Operation as ordinary delegation.
        for (var handler : node.methods) {
            if (!handler.desc.equals(HANDLER_DESC) || !owned(handler)) {
                continue;
            }
            handler.instructions.clear();
            handler.tryCatchBlocks.clear();
            if (handler.localVariables != null) {
                handler.localVariables.clear();
            }
            handler.visibleLocalVariableAnnotations = null;
            handler.invisibleLocalVariableAnnotations = null;
            handler.instructions.add(new InsnNode(ICONST_0));
            handler.instructions.add(new VarInsnNode(ALOAD, 0));
            handler.instructions.add(new VarInsnNode(ILOAD, 1));
            handler.instructions.add(new VarInsnNode(ALOAD, 2));
            handler.instructions.add(new VarInsnNode(ALOAD, 3));
            handler.instructions.add(new VarInsnNode(ALOAD, 4));
            handler.instructions.add(new InsnNode(FCONST_0));
            handler.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                                        "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireArmorWearCompatibility",
                                                        "route",
                                                        ROUTE_DESC,
                                                        false));
            handler.instructions.add(new InsnNode(RETURN));
            handler.maxStack = 7;
        }

        return false;
    }

    private static boolean caller(ClassNode node) throws AnalyzerException {
        var method = method(node, "onArmorHurt", "(Lnet/minecraft/world/damagesource/DamageSource;[" + SLOT + "F" + ENTITY + ")V");
        if (method == null || (method.access & ACC_STATIC) == 0) {
            return false;
        }

        var flow = new Flow(node, method);
        MethodInsnNode dispatch = null;
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("java/util/Map") || !call.name.equals("forEach")
                    || !call.desc.equals("(Ljava/util/function/BiConsumer;)V")) {
                continue;
            }

            var consumer = flow.origin(flow.argument(call, 0, 1));
            if (!(consumer instanceof InvokeDynamicInsnNode lambda) || !lambda.desc.equals("(" + ENTITY + ")Ljava/util/function/BiConsumer;")) {
                continue;
            }

            int targets = 0;
            for (Object argument : lambda.bsmArgs) {
                if (argument instanceof Handle handle && handle.getTag() == H_INVOKESTATIC && handle.getOwner().equals(OWNER)
                        && handle.getName().equals(LAMBDA) && handle.getDesc().equals("(" + ENTITY + SLOT + ENTRY + ")V")) {
                    targets++;
                }
            }
            if (targets != 1 || dispatch != null || !flow.parameter(flow.argument(lambda, 0, 1), 3)) {
                return false;
            }

            var map = flow.origin(flow.receiver(call, 1));
            if (!(map instanceof MethodInsnNode getMap) || !getMap.owner.equals(EVENT) || !getMap.name.equals("getArmorMap")
                    || !getMap.desc.equals("()Ljava/util/Map;")) {
                return false;
            }

            var posted = flow.origin(flow.receiver(getMap, 0));
            if (!(posted instanceof MethodInsnNode post) || !post.owner.equals("net/neoforged/bus/api/IEventBus")
                    || !post.name.equals("post") || !post.desc.equals("(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;")) {
                return false;
            }

            var created = flow.origin(flow.argument(post, 0, 1));
            if (!(created instanceof TypeInsnNode allocation) || allocation.getOpcode() != NEW || !allocation.desc.equals(EVENT)) {
                return false;
            }

            boolean constructor = false;
            for (var candidate : method.instructions) {
                if (candidate instanceof MethodInsnNode init && init.owner.equals(EVENT) && init.name.equals("<init>")
                        && init.desc.equals("(Ljava/util/EnumMap;" + ENTITY + "Lnet/minecraft/world/damagesource/DamageSource;)V")
                        && flow.origin(flow.receiver(init, 3)) == created && flow.parameter(flow.argument(init, 1, 3), 3)
                        && flow.parameter(flow.argument(init, 2, 3), 0)) {
                    constructor = true;
                }
            }
            if (!constructor || !cancelledBefore(flow, post, call)) {
                return false;
            }
            dispatch = call;
        }

        return dispatch != null;
    }

    private static boolean cancelledBefore(Flow flow, MethodInsnNode post, MethodInsnNode dispatch) {
        for (var instruction : flow.method.instructions) {
            if (!(instruction instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ && branch.getOpcode() != IFNE) {
                continue;
            }

            var result = flow.origin(flow.top(branch));
            if (!(result instanceof MethodInsnNode check) || !check.owner.equals(EVENT) || !check.name.equals("isCanceled")
                    || !check.desc.equals("()Z") || flow.origin(flow.receiver(check, 0)) != post) {
                continue;
            }

            var cancelled = branch.getOpcode() == IFEQ ? branch.getNext() : branch.label;
            var allowed = branch.getOpcode() == IFEQ ? branch.label : branch.getNext();
            if (!reachable(flow.method, flow.method.instructions.getFirst(), branch, post)
                    && !reachable(flow.method, flow.method.instructions.getFirst(), dispatch, branch)
                    && !reachable(flow.method, cancelled, dispatch, null) && reachable(flow.method, allowed, dispatch, null)) {
                return true;
            }
        }

        return false;
    }

    private static boolean nativeConsumer(ClassNode node) throws AnalyzerException {
        var method = method(node, LAMBDA, "(" + ENTITY + SLOT + ENTRY + ")V");
        if (method == null || (method.access & ACC_STATIC) == 0 || !straight(method)) {
            return false;
        }

        var flow = new Flow(node, method);
        int calls = 0;
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !damage(call)) {
                continue;
            }
            if (!flow.field(flow.receiver(call, 3), "armorItemStack", STACK, 2)
                    || !flow.convertedDamage(flow.argument(call, 0, 3), 2)
                    || !flow.parameter(flow.argument(call, 1, 3), 0) || !flow.parameter(flow.argument(call, 2, 3), 1)) {
                return false;
            }
            calls++;
        }

        return calls == 1;
    }

    private static boolean transformedConsumer(ClassNode node) throws AnalyzerException {
        var lambda = method(node, LAMBDA, "(" + ENTITY + SLOT + ENTRY + ")V");
        if (lambda == null || !straight(lambda)) {
            return false;
        }

        var flow = new Flow(node, lambda);
        int routes = 0;
        for (var instruction : lambda.instructions) {
            if (instruction instanceof MethodInsnNode call && damage(call)) {
                return false;
            }
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(OWNER)
                    || !call.desc.equals(HANDLER_DESC) && !call.desc.equals(FORWARD_DESC)) {
                continue;
            }

            var handler = method(node, call.name, call.desc);
            boolean direct = call.desc.equals(HANDLER_DESC);
            int count = direct ? 8 : 5;
            if (handler == null || !flow.field(flow.argument(call, 0, count), "armorItemStack", STACK, 2)
                    || !flow.convertedDamage(flow.argument(call, 1, count), 2)
                    || !flow.parameter(flow.argument(call, 2, count), 0) || !flow.parameter(flow.argument(call, 3, count), 1)) {
                return false;
            }

            var producer = flow.origin(flow.argument(call, 4, count));
            if (direct) {
                if (!owned(handler) || !router(node, handler)
                        || !flow.parameter(flow.argument(call, 5, 8), 0) || !flow.parameter(flow.argument(call, 6, 8), 1)
                        || !flow.parameter(flow.argument(call, 7, 8), 2)
                        || !operation(node, producer) && !forwardingOperation(node, flow, producer, false)) {
                    return false;
                }
            } else if (!forwarder(node, handler) || !forwardingOperation(node, flow, producer, true)) {
                return false;
            }
            routes++;
        }

        return routes == 1;
    }

    /** Bounded MixinExtras chain: one unchanged forwarding wrapper, inside OR outside our router. */
    private static boolean forwardingOperation(ClassNode node, Flow parent, AbstractInsnNode producer, boolean ownedInside) throws AnalyzerException {
        int captures = ownedInside ? 4 : 1;
        String captured = OPERATION + (ownedInside ? ENTITY + SLOT + ENTRY : "");
        if (!(producer instanceof InvokeDynamicInsnNode indy) || !indy.desc.equals("(" + captured + ")" + OPERATION)
                || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !operation(node, parent.origin(parent.argument(indy, 0, captures)))) {
            return false;
        }
        if (ownedInside && (!parent.parameter(parent.argument(indy, 1, captures), 0)
                || !parent.parameter(parent.argument(indy, 2, captures), 1) || !parent.parameter(parent.argument(indy, 3, captures), 2))) {
            return false;
        }

        MethodNode bridge = null;
        for (Object argument : indy.bsmArgs) {
            if (!(argument instanceof Handle handle)) {
                continue;
            }
            if (bridge != null || handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(OWNER)
                    || !handle.getDesc().equals("(" + captured + "[Ljava/lang/Object;)Ljava/lang/Void;")) {
                return false;
            }
            bridge = method(node, handle.getName(), handle.getDesc());
        }
        if (bridge == null || !straight(bridge)) {
            return false;
        }

        var flow = new Flow(node, bridge);
        int forwards = 0;
        for (var instruction : bridge.instructions) {
            if (instruction.getOpcode() >= IASTORE && instruction.getOpcode() <= SASTORE
                    || instruction.getOpcode() == PUTFIELD || instruction.getOpcode() == PUTSTATIC) {
                return false;
            }
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("intValue") && call.desc.equals("()I")) {
                continue;
            }
            if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime") && call.name.equals("checkArgumentCount")) {
                continue;
            }

            String desc = ownedInside ? HANDLER_DESC : FORWARD_DESC;
            int count = ownedInside ? 8 : 5;
            var target = method(node, call.name, call.desc);
            if (call.getOpcode() != INVOKESTATIC || !call.owner.equals(OWNER) || !call.desc.equals(desc) || target == null
                    || (ownedInside ? !owned(target) || !router(node, target) : !forwarder(node, target))) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (!flow.arrayElement(flow.argument(call, i, count), i, captures)) {
                    return false;
                }
            }
            for (int i = 0; i < captures; i++) {
                if (!flow.parameter(flow.argument(call, i + 4, count), i)) {
                    return false;
                }
            }
            forwards++;
        }

        return forwards == 1;
    }

    private static boolean forwarder(ClassNode node, MethodNode handler) throws AnalyzerException {
        if ((handler.access & ACC_STATIC) == 0 || !straight(handler)) {
            return false;
        }

        var flow = new Flow(node, handler);
        MethodInsnNode forward = null;
        AbstractInsnNode array = null;
        for (var instruction : handler.instructions) {
            if (instruction.getOpcode() == PUTFIELD || instruction.getOpcode() == PUTSTATIC) {
                return false;
            }
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            // Prove argument forwarding only; zero-argument callbacks' external effects are not audited.
            if (call.getOpcode() == INVOKESTATIC && call.desc.equals("()V")) {
                continue;
            }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("valueOf") && call.desc.equals("(I)Ljava/lang/Integer;")) {
                continue;
            }
            if (!call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") || !call.name.equals("call")
                    || !call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;") || !flow.parameter(flow.receiver(call, 1),
                                                                                                       4) || forward != null) {
                return false;
            }
            array = flow.origin(flow.argument(call, 0, 1));
            forward = call;
        }
        if (!(array instanceof TypeInsnNode allocation) || allocation.getOpcode() != ANEWARRAY || !allocation.desc.equals("java/lang/Object")
                || flow.constant(flow.top(allocation)) != 4) {
            return false;
        }

        var slots = new HashSet<Integer>();
        for (var instruction : handler.instructions) {
            if (instruction.getOpcode() != AASTORE) {
                continue;
            }
            int index = flow.constant(flow.argument(instruction, 1, 3));
            var value = flow.argument(instruction, 2, 3);
            var source = flow.origin(value);
            if (source instanceof MethodInsnNode box && box.owner.equals("java/lang/Integer") && box.name.equals("valueOf")) {
                value = flow.argument(box, 0, 1);
            }
            if (index < 0 || index > 3 || !slots.add(index) || flow.origin(flow.argument(instruction, 0, 3)) != array
                    || !flow.parameter(value, index) || !reachable(handler, instruction, forward, null)) {
                return false;
            }
        }

        return slots.size() == 4;
    }

    private static boolean straight(MethodNode method) {
        if (!method.tryCatchBlocks.isEmpty()) {
            return false;
        }

        int returns = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode
                    || instruction.getOpcode() == ATHROW) {
                return false;
            }
            if (instruction.getOpcode() >= IRETURN && instruction.getOpcode() <= RETURN) {
                returns++;
            }
        }

        return returns == 1;
    }

    private static boolean router(ClassNode node, MethodNode handler) throws AnalyzerException {
        if (!straight(handler)) {
            return false;
        }

        var flow = new Flow(node, handler);
        int routes = 0;
        int getters = 0;
        for (var instruction : handler.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if (call.owner.equals(OWNER) && call.name.equals(GETTER) && call.desc.equals("()Z")) {
                getters++;
                continue;
            }
            if (!call.owner.equals("games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireArmorWearCompatibility")
                    || !call.name.equals("route") || !call.desc.equals(ROUTE_DESC)) {
                return false;
            }

            var permission = flow.origin(flow.argument(call, 0, 7));
            if (!(permission instanceof MethodInsnNode getter) || !getter.owner.equals(OWNER) || !getter.name.equals(GETTER)
                    || !getter.desc.equals("()Z")) {
                return false;
            }
            for (int i = 0; i < 5; i++) {
                if (!flow.parameter(flow.argument(call, i + 1, 7), i)) {
                    return false;
                }
            }
            if (!flow.field(flow.argument(call, 6, 7), "originalDamage", "F", 7)) {
                return false;
            }
            for (var returned : handler.instructions) {
                if (returned.getOpcode() == RETURN && reachable(handler, handler.instructions.getFirst(), returned, call)) {
                    return false;
                }
            }
            routes++;
        }

        return routes == 1 && getters == 1;
    }

    private static boolean operation(ClassNode node, AbstractInsnNode producer) throws AnalyzerException {
        if (!(producer instanceof InvokeDynamicInsnNode indy) || !indy.desc.equals("()" + OPERATION)
                || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
            return false;
        }

        MethodNode callback = null;
        for (Object argument : indy.bsmArgs) {
            if (argument instanceof Handle handle) {
                if (callback != null || handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(OWNER)
                        || !handle.getDesc().equals("([Ljava/lang/Object;)Ljava/lang/Void;")) {
                    return false;
                }
                callback = method(node, handle.getName(), handle.getDesc());
            }
        }
        if (callback == null || !straight(callback)) {
            return false;
        }

        var flow = new Flow(node, callback);
        int damage = 0;
        for (var instruction : callback.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode >= IASTORE && opcode <= SASTORE || opcode == PUTFIELD || opcode == PUTSTATIC) {
                return false;
            }
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("intValue") && call.desc.equals("()I")) {
                continue;
            }
            if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime") && call.name.equals("checkArgumentCount")) {
                continue;
            }
            if (!damage(call) || !flow.arrayElement(flow.receiver(call, 3), 0)
                    || !flow.arrayElement(flow.argument(call, 0, 3), 1) || !flow.arrayElement(flow.argument(call, 1, 3), 2)
                    || !flow.arrayElement(flow.argument(call, 2, 3), 3)) {
                return false;
            }
            for (var returned : callback.instructions) {
                if (returned.getOpcode() == ARETURN && reachable(callback, callback.instructions.getFirst(), returned, call)) {
                    return false;
                }
            }
            damage++;
        }

        return damage == 1;
    }

    private static boolean damage(MethodInsnNode call) {
        return call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("hurtAndBreak") && call.desc.equals(DAMAGE_DESC);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }

    private static java.util.List<AbstractInsnNode> real(MethodNode method) {
        var result = new java.util.ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static boolean owned(MethodNode method) {
        return method.visibleAnnotations != null && method.visibleAnnotations.stream()
                                                                             .anyMatch(a -> a.desc.equals(
                                                                                     "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                                                                                     && a.values != null && a.values.contains(MIXIN));
    }

    private static boolean reachable(MethodNode method, AbstractInsnNode start, AbstractInsnNode end, AbstractInsnNode blocked) {
        var pending = new ArrayDeque<AbstractInsnNode>();
        var seen = new HashSet<AbstractInsnNode>();
        if (start != null) {
            pending.add(start);
        }
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (current == blocked || !seen.add(current)) {
                continue;
            }
            if (current == end) {
                return true;
            }

            int opcode = current.getOpcode();
            if (current instanceof JumpInsnNode jump) {
                pending.add(jump.label);
            }
            if (current instanceof TableSwitchInsnNode table) {
                pending.add(table.dflt);
                pending.addAll(table.labels);
            }
            if (current instanceof LookupSwitchInsnNode table) {
                pending.add(table.dflt);
                pending.addAll(table.labels);
            }
            if (opcode != GOTO && opcode != ATHROW && opcode != RETURN && (opcode < IRETURN || opcode > ARETURN)
                    && !(current instanceof TableSwitchInsnNode) && !(current instanceof LookupSwitchInsnNode) && current.getNext() != null) {
                pending.add(current.getNext());
            }

            int index = method.instructions.indexOf(current);
            for (var block : method.tryCatchBlocks) {
                if (index >= method.instructions.indexOf(block.start) && index < method.instructions.indexOf(block.end)) {
                    pending.add(block.handler);
                }
            }
        }

        return false;
    }

    private static final class Flow {
        final MethodNode method;
        final Frame<SourceValue>[] frames;

        Flow(ClassNode node, MethodNode method) throws AnalyzerException {
            this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override
                public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                    // Keep exact origins through stack shuffles in MixinExtras' Object[] bridge.
                    int opcode = instruction.getOpcode();
                    return opcode >= DUP && opcode <= SWAP ? value : super.copyOperation(instruction, value);
                }
                // MixinExtras has inserted arguments before ClassWriter recomputes stack maxima.
            }).analyzeAndComputeMaxs(node.name, method);
        }

        Frame<SourceValue> at(AbstractInsnNode node) {
            return frames[method.instructions.indexOf(node)];
        }

        SourceValue top(AbstractInsnNode node) {
            var frame = at(node);
            return frame == null ? null : frame.getStack(frame.getStackSize() - 1);
        }

        SourceValue argument(AbstractInsnNode node, int argument, int count) {
            var frame = at(node);
            return frame == null ? null : frame.getStack(frame.getStackSize() - count + argument);
        }

        SourceValue receiver(AbstractInsnNode node, int count) {
            var frame = at(node);
            return frame == null ? null : frame.getStack(frame.getStackSize() - count - 1);
        }

        AbstractInsnNode origin(SourceValue value) {
            return origin(value, new HashSet<>());
        }

        private AbstractInsnNode origin(SourceValue value, Set<AbstractInsnNode> seen) {
            if (value == null || value.insns.size() != 1) {
                return null;
            }

            var source = value.insns.iterator().next();
            if (!seen.add(source)) {
                return null;
            }
            if (source instanceof VarInsnNode local && source.getOpcode() >= ILOAD && source.getOpcode() <= ALOAD) {
                var original = at(source).getLocal(local.var);
                return original.insns.isEmpty() ? source : origin(original, seen);
            }
            if (source instanceof VarInsnNode && source.getOpcode() >= ISTORE && source.getOpcode() <= ASTORE
                    || source.getOpcode() == CHECKCAST || source.getOpcode() == DUP) {
                return origin(top(source), seen);
            }

            return source;
        }

        boolean parameter(SourceValue value, int parameter) {
            var source = origin(value);
            return source instanceof VarInsnNode local && local.var == parameter && at(source).getLocal(parameter).insns.isEmpty();
        }

        boolean field(SourceValue value, String name, String desc, int parameter) {
            var source = origin(value);
            return source instanceof FieldInsnNode field && field.getOpcode() == GETFIELD && field.owner.equals(EVENT + "$ArmorEntry")
                    && field.name.equals(name) && field.desc.equals(desc) && parameter(top(field), parameter);
        }

        boolean convertedDamage(SourceValue value, int parameter) {
            var source = origin(value);
            return source != null && source.getOpcode() == F2I && field(top(source), "newDamage", "F", parameter);
        }

        int constant(SourceValue value) {
            var source = origin(value);
            return source instanceof IntInsnNode constant ? constant.operand
                                                          : source != null && source.getOpcode() >= ICONST_M1 && source.getOpcode() <= ICONST_5
                                                            ? source.getOpcode() - ICONST_0
                                                            : Integer.MIN_VALUE;
        }

        boolean arrayElement(SourceValue value, int index) {
            return arrayElement(value, index, 0);
        }

        boolean arrayElement(SourceValue value, int index, int parameter) {
            var source = origin(value);
            if (source instanceof MethodInsnNode unbox && unbox.owner.equals("java/lang/Integer") && unbox.name.equals("intValue")) {
                source = origin(receiver(unbox, 0));
            }
            if (source == null || source.getOpcode() != AALOAD || !parameter(receiver(source, 1), parameter)) {
                return false;
            }
            var subscript = origin(top(source));
            return subscript instanceof IntInsnNode constant ? constant.operand == index
                                                             : subscript != null && subscript.getOpcode() == ICONST_0 + index;
        }
    }
}
