package games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture;

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

/** Final local consumption and observation proof. Only the audited Boots router is admitted. */
public final class MysticalRefundShape implements Opcodes {
    static final String OWNER = "net/minecraft/world/item/ItemStack";
    static final String STACK = "L" + OWNER + ";";
    static final String OP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    static final String READY = "mismatchedleggings$maRefundReady";
    private static final String COMPAT = "games/alejandrocoria/mismatchedleggings/compatibility/mysticalagriculture/MysticalRefundCompatibility";
    private static final String CAPTURE = "L" + COMPAT + "$Capture;";
    private static final String REF = "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;";
    private static final String MIXIN = "games.alejandrocoria.mismatchedleggings.mixin.compatibility.mysticalagriculture.MysticalRefundMixin";
    final String ENTITY;
    final String APPLY;
    final String HANDLER;
    private final String OBSERVER;

    public MysticalRefundShape(boolean fabric) {
        ENTITY = fabric ? "Lnet/minecraft/server/level/ServerPlayer;" : "Lnet/minecraft/world/entity/LivingEntity;";
        APPLY = "(I" + ENTITY + "Ljava/util/function/Consumer;)V";
        HANDLER = "(" + STACK + "I" + OP + "I" + ENTITY + "Ljava/util/function/Consumer;)V";
        OBSERVER = "(I" + ENTITY + "Ljava/util/function/Consumer;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + REF + ")V";
    }

    public boolean entry(ClassNode node) {
        try { return slice(node, false); }
        catch (AnalyzerException | RuntimeException incompatible) { return false; }
    }

    public boolean publish(ClassNode node) {
        var getter = method(node, READY, "()Z");
        boolean intact = getter != null && owned(getter) && getter.access == (ACC_PRIVATE | ACC_STATIC)
                && getter.tryCatchBlocks.isEmpty() && real(getter).size() == 2
                && real(getter).getFirst().getOpcode() == ICONST_0 && real(getter).getLast().getOpcode() == IRETURN;
        if (getter != null && owned(getter)) {
            getter.instructions.clear(); getter.tryCatchBlocks.clear(); getter.maxStack = 1;
            getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN));
        }
        try {
            if (intact && positionArm(node) && slice(node, true)) {
                getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1));
                return true;
            }
        } catch (AnalyzerException | RuntimeException incompatible) { /* Local refusal. */ }
        // Even a changed own handler must not bypass the readiness marker.
        for (var handler : node.methods) {
            if (owned(handler) && handler.desc.equals(OBSERVER)) {
                handler.instructions.clear(); handler.tryCatchBlocks.clear();
                if (handler.localVariables != null) { handler.localVariables.clear(); }
                handler.visibleLocalVariableAnnotations = null; handler.invisibleLocalVariableAnnotations = null;
                handler.instructions.add(new InsnNode(RETURN)); handler.maxStack = 0;
            }
        }
        return false;
    }

    /** Position only our passive arm after pre-break injections, in either sibling mixin order. */
    private boolean positionArm(ClassNode node) throws AnalyzerException {
        var apply = method(node, "applyDamage", APPLY);
        if (apply == null) { return false; }
        MethodInsnNode arm = null, site = null;
        for (var instruction : apply.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            var handler = call.owner.equals(OWNER) ? method(node, call.name, call.desc) : null;
            if (handler != null && "arm".equals(observer(handler))) {
                if (arm != null) { return false; } arm = call;
            }
            if (shrink(call) || handler != null && call.desc.equals(HANDLER) && boots(handler)) {
                if (site != null) { return false; } site = call;
            }
        }
        if (arm == null || site == null || apply.instructions.indexOf(arm) >= apply.instructions.indexOf(site)) { return false; }
        var loads = new ArrayList<AbstractInsnNode>();
        AbstractInsnNode first = arm;
        for (int i = 0; i < 6; i++) {
            first = first.getPrevious();
            if (first == null || first.getOpcode() < 0) { return false; }
            loads.addFirst(first);
        }
        if (!load(loads.get(0), ALOAD, 0) || !load(loads.get(1), ILOAD, 1)
                || !load(loads.get(2), ALOAD, 2) || !load(loads.get(3), ALOAD, 3)
                || loads.get(4).getOpcode() != ACONST_NULL || !(loads.get(5) instanceof VarInsnNode ref) || ref.getOpcode() != ALOAD) { return false; }
        var block = new org.objectweb.asm.tree.InsnList();
        for (var load : loads) { apply.instructions.remove(load); block.add(load); }
        apply.instructions.remove(arm); block.add(arm);
        apply.instructions.insertBefore(site, block);
        return true;
    }

    private boolean slice(ClassNode node, boolean transformed) throws AnalyzerException {
        var apply = method(node, "applyDamage", APPLY);
        if (!node.name.equals(OWNER) || apply == null || (apply.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0
                || !apply.tryCatchBlocks.isEmpty() || !consumption(node)) { return false; }
        var flow = new Flow(node, apply);
        MethodInsnNode site = null, write = null, callback = null, itemRead = null;
        var observations = new java.util.HashMap<String, MethodInsnNode>();
        AbstractInsnNode shared = null;
        for (var instruction : apply.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) { continue; }
            if (call.owner.equals(OWNER) && call.name.equals("setDamageValue") && call.desc.equals("(I)V")) {
                if (write != null || !flow.parameter(flow.receiver(call, 1), 0) || !flow.parameter(flow.top(call), 1)) { return false; }
                write = call;
            }
            if (call.owner.equals("java/util/function/Consumer") && call.name.equals("accept") && call.desc.equals("(Ljava/lang/Object;)V")) {
                var item = flow.origin(flow.top(call));
                if (callback != null || !flow.parameter(flow.receiver(call, 1), 3)
                        || !(item instanceof MethodInsnNode read) || !read.owner.equals(OWNER) || !read.name.equals("getItem")
                        || !read.desc.equals("()Lnet/minecraft/world/item/Item;") || !flow.parameter(flow.receiver(read, 0), 0)) { return false; }
                callback = call; itemRead = read;
            }
            if (shrink(call)) {
                if (site != null || !flow.parameter(flow.receiver(call, 1), 0) || flow.constant(flow.top(call)) != 1) { return false; }
                site = call;
            } else if (call.owner.equals(OWNER) && call.desc.equals(HANDLER)) {
                var handler = method(node, call.name, call.desc);
                if (site != null || handler == null || !boots(handler)
                        || !flow.parameter(flow.receiver(call, 6), 0) || !flow.parameter(flow.argument(call, 0, 6), 0)
                        || flow.constant(flow.argument(call, 1, 6)) != 1 || !flow.parameter(flow.argument(call, 3, 6), 1)
                        || !flow.parameter(flow.argument(call, 4, 6), 2) || !flow.parameter(flow.argument(call, 5, 6), 3)
                        || !operation(node, flow.origin(flow.argument(call, 2, 6)))) { return false; }
                site = call;
            } else if (call.owner.equals(OWNER) && call.desc.equals(OBSERVER)
                    && method(node, call.name, call.desc) != null && owned(method(node, call.name, call.desc))) {
                var handler = method(node, call.name, call.desc);
                String kind = handler == null ? null : observer(handler);
                var ref = flow.origin(flow.argument(call, 4, 5));
                if (!transformed || kind == null || observations.put(kind, call) != null
                        || !flow.parameter(flow.receiver(call, 5), 0)
                        || !flow.parameter(flow.argument(call, 0, 5), 1) || !flow.parameter(flow.argument(call, 1, 5), 2)
                        || !flow.parameter(flow.argument(call, 2, 5), 3)
                        || ref == null || shared != null && ref != shared) { return false; }
                shared = ref;
            }
        }
        if (site == null || write == null || callback == null || itemRead == null
                || reachable(apply, apply.instructions.getFirst(), site, write)
                || reachable(apply, apply.instructions.getFirst(), site, itemRead)
                || reachable(apply, apply.instructions.getFirst(), callback, site)) { return false; }
        for (var returned : apply.instructions) {
            if (returned.getOpcode() == RETURN && reachable(apply, site, returned, callback)) { return false; }
        }
        if (transformed) {
            if (observations.size() != 4 || !(shared instanceof TypeInsnNode allocation) || allocation.getOpcode() != NEW
                    || !allocation.desc.startsWith("com/llamalad7/mixinextras/sugar/impl/ref/")) { return false; }
            var capture = observations.get("capture"); var arm = observations.get("arm");
            var consumed = observations.get("consumed"); var complete = observations.get("complete");
            if (capture == null || arm == null || consumed == null || complete == null
                    || reachable(apply, apply.instructions.getFirst(), write, capture)
                    || reachable(apply, apply.instructions.getFirst(), site, arm)
                    || reachable(apply, apply.instructions.getFirst(), arm, itemRead)
                    || reachable(apply, apply.instructions.getFirst(), consumed, site)
                    || reachable(apply, apply.instructions.getFirst(), callback, consumed)
                    || reachable(apply, site, complete, callback)
                    || reachable(apply, site.getNext(), arm, null)) { return false; }
            for (var returned : apply.instructions) {
                if (returned.getOpcode() == RETURN && reachable(apply, site, returned, complete)) { return false; }
            }
            // No mutable/opaque operation between the last eligibility observation and consumption.
            for (var instruction : apply.instructions) {
                if (instruction instanceof MethodInsnNode call && call != arm && call != site
                        && reachable(apply, arm.getNext(), call, site) && reachable(apply, call, site, null)) { return false; }
            }
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

    private String observer(MethodNode handler) {
        if (!owned(handler) || (handler.access & ACC_STATIC) != 0 || !straight(handler)) { return null; }
        var code = real(handler);
        if (code.size() == 8 && load(code.get(0), ALOAD, 5)
                && call(code.get(1), INVOKESTATIC, OWNER, READY, "()Z")
                && load(code.get(2), ALOAD, 0) && load(code.get(3), ILOAD, 1) && load(code.get(4), ALOAD, 2)
                && call(code.get(5), INVOKESTATIC, COMPAT, "capture", "(Z" + STACK + "ILnet/minecraft/world/entity/LivingEntity;)" + CAPTURE)
                && call(code.get(6), INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "set", "(Ljava/lang/Object;)V")
                && code.get(7).getOpcode() == RETURN) { return "capture"; }
        if (code.size() == 5 && load(code.get(0), ALOAD, 5)
                && call(code.get(1), INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "get", "()Ljava/lang/Object;")
                && code.get(2) instanceof TypeInsnNode cast && cast.getOpcode() == CHECKCAST && cast.desc.equals(COMPAT + "$Capture")
                && code.get(3) instanceof MethodInsnNode action && action.getOpcode() == INVOKESTATIC && action.owner.equals(COMPAT)
                && Set.of("arm", "consumed", "complete").contains(action.name) && action.desc.equals("(" + CAPTURE + ")V")
                && code.get(4).getOpcode() == RETURN) { return action.name; }
        return null;
    }

    private boolean boots(MethodNode handler) {
        String peer = "games.alejandrocoria.mismatchedboots.mixin.compatibility.mysticalagriculture.MysticalRefundMixin";
        if (handler.visibleAnnotations == null || handler.visibleAnnotations.stream().noneMatch(a ->
                a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && a.values != null && a.values.contains(peer))
                || !straight(handler)) { return false; }
        var code = real(handler);
        return code.size() == 7 && call(code.get(0), INVOKESTATIC, OWNER, "mismatchedboots$maRefundReady", "()Z")
                && load(code.get(1), ALOAD, 1) && load(code.get(2), ILOAD, 2) && load(code.get(3), ALOAD, 5) && load(code.get(4), ALOAD, 3)
                && call(code.get(5), INVOKESTATIC, "games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalRefundCompatibility",
                        "route", "(Z" + STACK + "ILnet/minecraft/world/entity/LivingEntity;" + OP + ")V")
                && code.get(6).getOpcode() == RETURN;
    }

    private boolean consumption(ClassNode node) {
        var shrink = method(node, "shrink", "(I)V"); var grow = method(node, "grow", "(I)V"); var count = method(node, "setCount", "(I)V");
        if (shrink == null || grow == null || count == null || !straight(shrink) || !straight(grow) || !straight(count)) { return false; }
        var s = real(shrink); var g = real(grow); var c = real(count);
        return s.size() == 5 && load(s.get(0), ALOAD, 0) && load(s.get(1), ILOAD, 1) && s.get(2).getOpcode() == INEG
                && call(s.get(3), INVOKEVIRTUAL, OWNER, "grow", "(I)V") && s.get(4).getOpcode() == RETURN
                && g.size() == 7 && load(g.get(0), ALOAD, 0) && load(g.get(1), ALOAD, 0)
                && call(g.get(2), INVOKEVIRTUAL, OWNER, "getCount", "()I") && load(g.get(3), ILOAD, 1) && g.get(4).getOpcode() == IADD
                && call(g.get(5), INVOKEVIRTUAL, OWNER, "setCount", "(I)V") && g.get(6).getOpcode() == RETURN
                && c.size() == 4 && load(c.get(0), ALOAD, 0) && load(c.get(1), ILOAD, 1)
                && c.get(2) instanceof org.objectweb.asm.tree.FieldInsnNode field && field.getOpcode() == PUTFIELD
                && field.owner.equals(OWNER) && field.name.equals("count") && field.desc.equals("I") && c.get(3).getOpcode() == RETURN;
    }

    private boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String desc) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc);
    }

    private boolean operation(ClassNode node, AbstractInsnNode producer) throws AnalyzerException {
        if (!(producer instanceof InvokeDynamicInsnNode indy) || !indy.desc.equals("()" + OP)
                || !indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
            return false;
        }
        MethodNode callback = null;
        for (Object argument : indy.bsmArgs) {
            if (!(argument instanceof Handle handle)) {
                continue;
            }
            if (callback != null || handle.getTag() != H_INVOKESTATIC || !handle.getOwner().equals(OWNER)
                    || !handle.getDesc().equals("([Ljava/lang/Object;)Ljava/lang/Void;")) {
                return false;
            }
            callback = method(node, handle.getName(), handle.getDesc());
        }
        if (callback == null || !straight(callback)) {
            return false;
        }
        var flow = new Flow(node, callback);
        int calls = 0;
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
                if (!flow.parameter(flow.argument(call, 0, 3), 0) || flow.constant(flow.argument(call, 1, 3)) != 2) {
                    return false;
                }
                continue;
            }
            if (!shrink(call) || !flow.arrayElement(flow.receiver(call, 1), 0, 0) || !flow.arrayElement(flow.top(call), 1, 0)) {
                return false;
            }
            calls++;
        }
        return calls == 1;
    }

    private boolean shrink(MethodInsnNode call) {
        return call.getOpcode() == INVOKEVIRTUAL && call.owner.equals(OWNER) && call.name.equals("shrink") && call.desc.equals("(I)V");
    }

    private boolean load(AbstractInsnNode node, int opcode, int slot) {
        return node instanceof VarInsnNode local && local.getOpcode() == opcode && local.var == slot;
    }

    private MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }

    private List<AbstractInsnNode> real(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0 && instruction.getOpcode() != NOP) {
                result.add(instruction);
            }
        }
        return result;
    }

    private boolean owned(MethodNode method) {
        return method.visibleAnnotations != null
                && method.visibleAnnotations.stream().anyMatch(annotation ->
                        annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                                && annotation.values != null && annotation.values.contains(MIXIN));
    }

    private boolean straight(MethodNode method) {
        return method.tryCatchBlocks.isEmpty()
                && real(method).stream().noneMatch(instruction -> instruction instanceof JumpInsnNode
                        || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode
                        || instruction.getOpcode() == ATHROW)
                && real(method).stream()
                               .filter(instruction -> instruction.getOpcode() >= IRETURN && instruction.getOpcode() <= RETURN)
                               .count() == 1;
    }

    private boolean reachable(MethodNode method, AbstractInsnNode start, AbstractInsnNode end, AbstractInsnNode blocked) {
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
            if (opcode != GOTO && opcode != ATHROW && (opcode < IRETURN || opcode > RETURN)
                    && !(current instanceof TableSwitchInsnNode) && !(current instanceof LookupSwitchInsnNode) && current.getNext() != null) {
                pending.add(current.getNext());
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
                    int opcode = instruction.getOpcode();
                    return opcode >= DUP && opcode <= SWAP ? value : super.copyOperation(instruction, value);
                }
            }).analyzeAndComputeMaxs(node.name, method);
        }

        Frame<SourceValue> at(AbstractInsnNode node) {
            return frames[method.instructions.indexOf(node)];
        }

        SourceValue top(AbstractInsnNode node) {
            return argument(node, 0, 1);
        }

        SourceValue argument(AbstractInsnNode node, int argument, int count) {
            var frame = at(node);
            return frame == null ? null : frame.getStack(frame.getStackSize() - count + argument);
        }

        SourceValue receiver(AbstractInsnNode node, int count) {
            return argument(node, 0, count + 1);
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
                var previous = at(source).getLocal(local.var);
                return previous.insns.isEmpty() ? source : origin(previous, seen);
            }
            if ((source instanceof VarInsnNode && source.getOpcode() >= ISTORE && source.getOpcode() <= ASTORE)
                    || source.getOpcode() == CHECKCAST) {
                return origin(top(source), seen);
            }
            return source;
        }

        boolean parameter(SourceValue value, int slot) {
            var source = origin(value);
            return source instanceof VarInsnNode local && local.var == slot && at(source).getLocal(slot).insns.isEmpty();
        }

        int constant(SourceValue value) {
            var source = origin(value);
            if (source instanceof IntInsnNode number) {
                return number.operand;
            }

            return source != null && source.getOpcode() >= ICONST_M1 && source.getOpcode() <= ICONST_5
                    ? source.getOpcode() - ICONST_0 : Integer.MIN_VALUE;
        }

        boolean arrayElement(SourceValue value, int index, int slot) {
            var source = origin(value);
            if (source instanceof MethodInsnNode unbox && unbox.owner.equals("java/lang/Integer") && unbox.name.equals("intValue")) {
                source = origin(receiver(unbox, 0));
            }
            return source != null && source.getOpcode() == AALOAD && parameter(receiver(source, 1), slot) && constant(top(source)) == index;
        }
    }
}
