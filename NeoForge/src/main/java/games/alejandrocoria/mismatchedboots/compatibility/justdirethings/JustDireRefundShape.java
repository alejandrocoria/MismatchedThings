package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only the exhausted-stack consumption/callback slice and our original-operation chain. */
public final class JustDireRefundShape implements Opcodes {
    static final String OWNER = "net/minecraft/world/item/ItemStack";
    static final String STACK = "L" + OWNER + ";";
    static final String ENTITY = "Lnet/minecraft/world/entity/LivingEntity;";
    static final String OP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    static final String APPLY = "(I" + ENTITY + "Ljava/util/function/Consumer;)V";
    static final String HANDLER = "(" + STACK + "I" + OP + "I" + ENTITY + "Ljava/util/function/Consumer;)V";
    private static final String FORWARD = "(" + STACK + "I" + OP + ")V";
    static final String READY = "mismatchedboots$justDireRefundReady";
    private static final String COMPAT = "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireRefundCompatibility";
    private static final String ROUTE = "(Z" + STACK + "I" + ENTITY + OP + ")V";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.compatibility.justdirethings.JustDireRefundMixin";

    private JustDireRefundShape() {}

    static boolean entry(ClassNode node) {
        try { return slice(node, false); }
        catch (AnalyzerException | RuntimeException incompatible) { return false; }
    }

    static boolean publish(ClassNode node) {
        var getter = method(node, READY, "()Z");
        boolean intact = getter != null && owned(getter) && getter.access == (ACC_PRIVATE | ACC_STATIC)
                && getter.tryCatchBlocks.isEmpty() && real(getter).size() == 2
                && real(getter).getFirst().getOpcode() == ICONST_0 && real(getter).getLast().getOpcode() == IRETURN;
        if (getter != null && owned(getter)) {
            getter.instructions.clear(); getter.tryCatchBlocks.clear(); getter.maxStack = 1;
            getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN));
        }
        try {
            if (intact && slice(node, true)) { getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1)); return true; }
        } catch (AnalyzerException | RuntimeException incompatible) { /* Keep independent ordinary consumption. */ }
        // A changed router could bypass the getter. Neutralize only our merged method, never foreign code.
        for (var handler : node.methods) {
            if (!handler.desc.equals(HANDLER) || !owned(handler)) { continue; }
            handler.instructions.clear(); handler.tryCatchBlocks.clear();
            if (handler.localVariables != null) { handler.localVariables.clear(); }
            handler.visibleLocalVariableAnnotations = null; handler.invisibleLocalVariableAnnotations = null;
            handler.instructions.add(new InsnNode(ICONST_0));
            handler.instructions.add(new VarInsnNode(ALOAD, 1)); handler.instructions.add(new VarInsnNode(ILOAD, 2));
            handler.instructions.add(new VarInsnNode(ALOAD, 5)); handler.instructions.add(new VarInsnNode(ALOAD, 3));
            handler.instructions.add(new MethodInsnNode(INVOKESTATIC, COMPAT, "route", ROUTE, false));
            handler.instructions.add(new InsnNode(RETURN)); handler.maxStack = 5;
        }
        return false;
    }

    private static boolean slice(ClassNode node, boolean transformed) throws AnalyzerException {
        var apply = method(node, "applyDamage", APPLY);
        if (!node.name.equals(OWNER) || apply == null || (apply.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0
                || !apply.tryCatchBlocks.isEmpty()) { return false; }
        var flow = new Flow(node, apply);
        MethodInsnNode site = null, write = null, callback = null, itemRead = null;
        for (var instruction : apply.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            if (call.owner.equals(OWNER) && call.name.equals("setDamageValue") && call.desc.equals("(I)V")) {
                if (write != null || !flow.parameter(flow.receiver(call, 1), 0) || !flow.parameter(flow.argument(call, 0, 1), 1)) { return false; }
                write = call;
            }
            if (call.owner.equals("java/util/function/Consumer") && call.name.equals("accept") && call.desc.equals("(Ljava/lang/Object;)V")) {
                var item = flow.origin(flow.argument(call, 0, 1));
                if (callback != null || !flow.parameter(flow.receiver(call, 1), 3)
                        || !(item instanceof MethodInsnNode read) || !read.owner.equals(OWNER) || !read.name.equals("getItem")
                        || !read.desc.equals("()Lnet/minecraft/world/item/Item;") || !flow.parameter(flow.receiver(read, 0), 0)) { return false; }
                callback = call;
                itemRead = read;
            }
            if (shrink(call)) {
                if (transformed || site != null || !flow.parameter(flow.receiver(call, 1), 0) || flow.constant(flow.top(call)) != 1) { return false; }
                site = call;
            } else if (call.owner.equals(OWNER) && call.desc.equals(HANDLER)) {
                var handler = method(node, call.name, call.desc);
                if (!transformed || site != null || handler == null || !observer(handler)
                        || !flow.parameter(flow.receiver(call, 6), 0) || !flow.parameter(flow.argument(call, 0, 6), 0)
                        || flow.constant(flow.argument(call, 1, 6)) != 1 || !flow.parameter(flow.argument(call, 3, 6), 1)
                        || !flow.parameter(flow.argument(call, 4, 6), 2) || !flow.parameter(flow.argument(call, 5, 6), 3)
                        || !operation(node, flow.origin(flow.argument(call, 2, 6)))
                        && !forwardingOperation(node, flow, flow.origin(flow.argument(call, 2, 6)), false)
                        && !forwardingOperation(node, flow, flow.origin(flow.argument(call, 2, 6)), true)) { return false; }
                site = call;
            } else if (call.owner.equals(OWNER) && call.desc.equals(FORWARD)) {
                var handler = method(node, call.name, call.desc);
                if (!transformed || site != null || handler == null || !forwarder(node, handler)
                        || !flow.parameter(flow.receiver(call, 3), 0) || !flow.parameter(flow.argument(call, 0, 3), 0)
                        || flow.constant(flow.argument(call, 1, 3)) != 1
                        || !forwardingOperation(node, flow, flow.origin(flow.argument(call, 2, 3)), true)) { return false; }
                site = call;
            }
        }
        if (site == null || write == null || callback == null
                || reachable(apply, apply.instructions.getFirst(), site, write)
                || reachable(apply, apply.instructions.getFirst(), site, itemRead)
                || reachable(apply, apply.instructions.getFirst(), callback, site)) { return false; }
        for (var returned : apply.instructions) {
            if (returned.getOpcode() == RETURN && reachable(apply, site, returned, callback)) { return false; }
        }
        for (var instruction : apply.instructions) {
            if (!(instruction instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ && branch.getOpcode() != IFNE) { continue; }
            var checked = flow.origin(flow.top(branch));
            if (!(checked instanceof MethodInsnNode broken) || !broken.owner.equals(OWNER) || !broken.name.equals("isBroken")
                    || !broken.desc.equals("()Z") || !flow.parameter(flow.receiver(broken, 0), 0)) { continue; }
            var allowed = branch.getOpcode() == IFEQ ? branch.getNext() : branch.label;
            var denied = branch.getOpcode() == IFEQ ? branch.label : branch.getNext();
            if (!reachable(apply, apply.instructions.getFirst(), branch, write)
                    && !reachable(apply, apply.instructions.getFirst(), site, branch)
                    && reachable(apply, allowed, site, null) && !reachable(apply, denied, site, null)) { return true; }
        }
        return false;
    }

    private static boolean observer(MethodNode handler) {
        if (owned(handler)) { return router(handler); }
        // Recognize our independent MA consumption observer, not its eligibility or refund items.
        String peer = "games.alejandrocoria.mismatchedboots.mixin.compatibility.mysticalagriculture.MysticalRefundMixin";
        if (handler.visibleAnnotations == null || handler.visibleAnnotations.stream()
                .noneMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                        && a.values != null && a.values.contains(peer))) { return false; }
        return routerCode(handler, "mismatchedboots$maRefundReady",
                "games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalRefundCompatibility");
    }

    private static boolean router(MethodNode handler) { return routerCode(handler, READY, COMPAT); }

    private static boolean routerCode(MethodNode handler, String marker, String adapter) {
        if ((handler.access & ACC_STATIC) != 0 || !handler.tryCatchBlocks.isEmpty()) { return false; }
        var code = real(handler);
        return code.size() == 7 && code.get(0) instanceof MethodInsnNode ready && ready.getOpcode() == INVOKESTATIC
                && ready.owner.equals(OWNER) && ready.name.equals(marker) && ready.desc.equals("()Z")
                && load(code.get(1), ALOAD, 1) && load(code.get(2), ILOAD, 2) && load(code.get(3), ALOAD, 5) && load(code.get(4), ALOAD, 3)
                && code.get(5) instanceof MethodInsnNode route && route.getOpcode() == INVOKESTATIC && route.owner.equals(adapter)
                && route.name.equals("route") && route.desc.equals(ROUTE) && code.get(6).getOpcode() == RETURN;
    }

    private static boolean operation(ClassNode node, AbstractInsnNode producer) throws AnalyzerException {
        if (!(producer instanceof InvokeDynamicInsnNode indy) || !indy.desc.equals("()" + OP)
                || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) { return false; }
        MethodNode callback = null;
        for (Object argument : indy.bsmArgs) {
            if (!(argument instanceof Handle handle)) { continue; }
            if (callback != null || handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(OWNER)
                    || !handle.getDesc().equals("([Ljava/lang/Object;)Ljava/lang/Void;")) { return false; }
            callback = method(node, handle.getName(), handle.getDesc());
        }
        if (callback == null || !straight(callback)) { return false; }
        var flow = new Flow(node, callback); int calls = 0;
        for (var instruction : callback.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode >= IASTORE && opcode <= SASTORE || opcode == PUTFIELD || opcode == PUTSTATIC) { return false; }
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("intValue") && call.desc.equals("()I")) { continue; }
            if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime") && call.name.equals("checkArgumentCount")) {
                if (!flow.parameter(flow.argument(call, 0, 3), 0) || flow.constant(flow.argument(call, 1, 3)) != 2) { return false; }
                continue;
            }
            if (!shrink(call) || !flow.arrayElement(flow.receiver(call, 1), 0, 0) || !flow.arrayElement(flow.top(call), 1, 0)) { return false; }
            calls++;
        }
        return calls == 1;
    }

    /** One unchanged forwarding wrapper inside OR outside our router, not an arbitrary wrapper analyzer. */
    private static boolean forwardingOperation(ClassNode node, Flow parent, AbstractInsnNode producer, boolean ownedInside) throws AnalyzerException {
        int captures = ownedInside ? 5 : 2;
        String captured = STACK + OP + (ownedInside ? "I" + ENTITY + "Ljava/util/function/Consumer;" : "");
        if (!(producer instanceof InvokeDynamicInsnNode indy) || !indy.desc.equals("(" + captured + ")" + OP)
                || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                || !parent.parameter(parent.argument(indy, 0, captures), 0)
                || !operation(node, parent.origin(parent.argument(indy, 1, captures)))) { return false; }
        if (ownedInside) { for (int i = 2; i < 5; i++) { if (!parent.parameter(parent.argument(indy, i, captures), i - 1)) { return false; } } }
        MethodNode bridge = null;
        for (Object argument : indy.bsmArgs) {
            if (!(argument instanceof Handle handle)) { continue; }
            boolean instance = handle.getTag() == H_INVOKEVIRTUAL || handle.getTag() == H_INVOKESPECIAL;
            if (bridge != null || !instance && handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(OWNER)
                    || !handle.getDesc().equals("(" + (instance ? captured.substring(STACK.length()) : captured) + "[Ljava/lang/Object;)Ljava/lang/Void;")) { return false; }
            bridge = method(node, handle.getName(), handle.getDesc());
            if (bridge == null || ((bridge.access & ACC_STATIC) == 0) != instance) { return false; }
        }
        if (bridge == null || !straight(bridge)) { return false; }
        var flow = new Flow(node, bridge); int calls = 0;
        for (var instruction : bridge.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode >= IASTORE && opcode <= SASTORE || opcode == PUTFIELD || opcode == PUTSTATIC) { return false; }
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("intValue") && call.desc.equals("()I")) { continue; }
            if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime") && call.name.equals("checkArgumentCount")) {
                if (!flow.parameter(flow.argument(call, 0, 3), captures) || flow.constant(flow.argument(call, 1, 3)) != 2) { return false; }
                continue;
            }
            String desc = ownedInside ? HANDLER : FORWARD; int arguments = ownedInside ? 6 : 3;
            var handler = method(node, call.name, call.desc);
            if (!call.owner.equals(OWNER) || !call.desc.equals(desc) || handler == null
                    || !flow.parameter(flow.receiver(call, arguments), 0)
                    || !flow.arrayElement(flow.argument(call, 0, arguments), 0, captures)
                    || !flow.arrayElement(flow.argument(call, 1, arguments), 1, captures)
                    || !flow.parameter(flow.argument(call, 2, arguments), 1)
                    || (ownedInside ? !observer(handler) : !forwarder(node, handler))) { return false; }
            if (ownedInside) { for (int i = 3; i < 6; i++) { if (!flow.parameter(flow.argument(call, i, arguments), i - 1)) { return false; } } }
            calls++;
        }
        return calls == 1;
    }

    private static boolean forwarder(ClassNode node, MethodNode handler) throws AnalyzerException {
        if ((handler.access & ACC_STATIC) != 0 || !straight(handler) || owned(handler)) { return false; }
        var flow = new Flow(node, handler); AbstractInsnNode array = null; MethodInsnNode forwarded = null; int calls = 0;
        for (var instruction : handler.instructions) {
            if (instruction.getOpcode() == PUTFIELD || instruction.getOpcode() == PUTSTATIC) { return false; }
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            // Only forwarding is proven; external effects of surrounding zero-argument callbacks are not audited.
            if (call.getOpcode() == INVOKESTATIC && call.desc.equals("()V")) { continue; }
            if (call.owner.equals("java/lang/Integer") && call.name.equals("valueOf") && call.desc.equals("(I)Ljava/lang/Integer;")) { continue; }
            if (!call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation") || !call.name.equals("call")
                    || !call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;") || !flow.parameter(flow.receiver(call, 1), 3)) { return false; }
            array = flow.origin(flow.top(call)); forwarded = call; calls++;
        }
        if (calls != 1 || !(array instanceof TypeInsnNode allocation) || allocation.getOpcode() != ANEWARRAY
                || !allocation.desc.equals("java/lang/Object") || flow.constant(flow.top(allocation)) != 2) { return false; }
        var slots = new HashSet<Integer>();
        for (var instruction : handler.instructions) {
            if (instruction.getOpcode() < IASTORE || instruction.getOpcode() > SASTORE) { continue; }
            if (instruction.getOpcode() != AASTORE) { return false; }
            int index = flow.constant(flow.argument(instruction, 1, 3)); var value = flow.argument(instruction, 2, 3);
            if (flow.origin(value) instanceof MethodInsnNode box && box.owner.equals("java/lang/Integer") && box.name.equals("valueOf")) { value = flow.argument(box, 0, 1); }
            if (index < 0 || index > 1 || !slots.add(index) || flow.origin(flow.argument(instruction, 0, 3)) != array || !flow.parameter(value, index + 1)
                    || handler.instructions.indexOf(instruction) >= handler.instructions.indexOf(forwarded)) { return false; }
        }
        return slots.size() == 2;
    }

    private static boolean shrink(MethodInsnNode call) { return call.getOpcode() == INVOKEVIRTUAL && call.owner.equals(OWNER) && call.name.equals("shrink") && call.desc.equals("(I)V"); }
    private static boolean load(AbstractInsnNode node, int opcode, int slot) { return node instanceof VarInsnNode local && local.getOpcode() == opcode && local.var == slot; }
    private static MethodNode method(ClassNode node, String name, String desc) { return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null); }
    private static List<AbstractInsnNode> real(MethodNode method) { var result = new ArrayList<AbstractInsnNode>(); for (var instruction : method.instructions) { if (instruction.getOpcode() >= 0 && instruction.getOpcode() != NOP) { result.add(instruction); } } return result; }
    private static boolean owned(MethodNode method) { return method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && a.values != null && a.values.contains(MIXIN)); }
    private static boolean straight(MethodNode method) { return method.tryCatchBlocks.isEmpty() && real(method).stream().noneMatch(n -> n instanceof JumpInsnNode || n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode || n.getOpcode() == ATHROW) && real(method).stream().filter(n -> n.getOpcode() >= IRETURN && n.getOpcode() <= RETURN).count() == 1; }

    private static boolean reachable(MethodNode method, AbstractInsnNode start, AbstractInsnNode end, AbstractInsnNode blocked) {
        var pending = new ArrayDeque<AbstractInsnNode>(); var seen = new HashSet<AbstractInsnNode>();
        if (start != null) { pending.add(start); }
        while (!pending.isEmpty()) {
            var current = pending.removeFirst(); if (current == blocked || !seen.add(current)) { continue; }
            if (current == end) { return true; }
            int opcode = current.getOpcode();
            if (current instanceof JumpInsnNode jump) { pending.add(jump.label); }
            if (current instanceof TableSwitchInsnNode table) { pending.add(table.dflt); pending.addAll(table.labels); }
            if (current instanceof LookupSwitchInsnNode table) { pending.add(table.dflt); pending.addAll(table.labels); }
            if (opcode != GOTO && opcode != ATHROW && (opcode < IRETURN || opcode > RETURN)
                    && !(current instanceof TableSwitchInsnNode) && !(current instanceof LookupSwitchInsnNode) && current.getNext() != null) { pending.add(current.getNext()); }
        }
        return false;
    }

    private static final class Flow {
        final MethodNode method;
        final Frame<SourceValue>[] frames;
        Flow(ClassNode node, MethodNode method) throws AnalyzerException {
            this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                    int opcode = instruction.getOpcode(); return opcode >= DUP && opcode <= SWAP ? value : super.copyOperation(instruction, value);
                }
            }).analyzeAndComputeMaxs(node.name, method);
        }
        Frame<SourceValue> at(AbstractInsnNode node) { return frames[method.instructions.indexOf(node)]; }
        SourceValue top(AbstractInsnNode node) { return argument(node, 0, 1); }
        SourceValue argument(AbstractInsnNode node, int argument, int count) { var f = at(node); return f == null ? null : f.getStack(f.getStackSize() - count + argument); }
        SourceValue receiver(AbstractInsnNode node, int count) { return argument(node, 0, count + 1); }
        AbstractInsnNode origin(SourceValue value) { return origin(value, new HashSet<>()); }
        private AbstractInsnNode origin(SourceValue value, Set<AbstractInsnNode> seen) {
            if (value == null || value.insns.size() != 1) { return null; }
            var source = value.insns.iterator().next(); if (!seen.add(source)) { return null; }
            if (source instanceof VarInsnNode local && source.getOpcode() >= ILOAD && source.getOpcode() <= ALOAD) {
                var previous = at(source).getLocal(local.var); return previous.insns.isEmpty() ? source : origin(previous, seen);
            }
            if (source instanceof VarInsnNode && source.getOpcode() >= ISTORE && source.getOpcode() <= ASTORE || source.getOpcode() == CHECKCAST) { return origin(top(source), seen); }
            return source;
        }
        boolean parameter(SourceValue value, int slot) { var source = origin(value); return source instanceof VarInsnNode local && local.var == slot && at(source).getLocal(slot).insns.isEmpty(); }
        int constant(SourceValue value) { var source = origin(value); return source instanceof IntInsnNode number ? number.operand : source != null && source.getOpcode() >= ICONST_M1 && source.getOpcode() <= ICONST_5 ? source.getOpcode() - ICONST_0 : Integer.MIN_VALUE; }
        boolean arrayElement(SourceValue value, int index, int slot) {
            var source = origin(value);
            if (source instanceof MethodInsnNode unbox && unbox.owner.equals("java/lang/Integer") && unbox.name.equals("intValue")) { source = origin(receiver(unbox, 0)); }
            return source != null && source.getOpcode() == AALOAD && parameter(receiver(source, 1), slot) && constant(top(source)) == index;
        }
    }
}
