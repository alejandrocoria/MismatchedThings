package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.HashSet;
import java.util.Set;

/** Proves only the event's root/default argument flow; unrelated hooks/debug nodes are irrelevant. */
public final class JustDireAttributesShape implements Opcodes {
    static final String OWNER = "net/neoforged/neoforge/common/CommonHooks";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String MODIFIERS = "Lnet/minecraft/world/item/component/ItemAttributeModifiers;";
    private static final String DESC = "(" + STACK + MODIFIERS + ")" + MODIFIERS;
    private static final String EVENT = "net/neoforged/neoforge/event/ItemAttributeModifierEvent";
    private static final String GETTER = "mismatchedboots$justDireAttributesReady";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.JustDireAttributesMixin";

    private JustDireAttributesShape() {}

    static boolean entry(ClassNode node) { return flow(node, false); }

    static boolean publish(ClassNode node) {
        MethodNode getter = method(node, GETTER, "()Z");
        if (getter == null) { return false; }
        var code = new java.util.ArrayList<AbstractInsnNode>();
        for (var instruction : getter.instructions) { if (instruction.getOpcode() >= 0) { code.add(instruction); } }
        boolean intact = owned(getter) && getter.access == (ACC_PRIVATE | ACC_STATIC) && code.size() == 2
                && code.getFirst().getOpcode() == ICONST_0 && code.getLast().getOpcode() == IRETURN;
        getter.instructions.clear(); getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN));
        getter.tryCatchBlocks.clear(); getter.maxStack = 1;
        if (!intact || !flow(node, true)) { return false; }
        getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    private static boolean flow(ClassNode node, boolean transformed) {
        MethodNode method = method(node, "computeModifiedAttributes", DESC);
        if (method == null || (method.access & ACC_STATIC) == 0) { return false; }
        try {
            Frame<SourceValue>[] frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, method);
            MethodInsnNode constructor = null, router = null, build = null;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(EVENT) && call.name.equals("<init>")
                        && call.desc.equals("(" + STACK + MODIFIERS + ")V")) {
                    if (constructor != null) { return false; } constructor = call;
                }
                if (instruction instanceof MethodInsnNode call && call.owner.equals(OWNER)
                        && call.name.contains("mismatchedboots$justDireAttributes") && call.desc.equals(DESC)) {
                    if (router != null) { return false; } router = call;
                }
                if (instruction instanceof MethodInsnNode call && call.owner.equals(EVENT) && call.name.equals("build")
                        && call.desc.equals("()" + MODIFIERS)) {
                    if (build != null) { return false; } build = call;
                }
            }
            if (constructor == null || build == null || transformed && router == null) { return false; }
            Frame<SourceValue> frame = frames[method.instructions.indexOf(constructor)];
            var buildFrame = frames[method.instructions.indexOf(build)];
            AbstractInsnNode created = frame == null ? null : creation(method, frames, frame.getStack(frame.getStackSize() - 3), new HashSet<>());
            if (created == null || buildFrame == null || creation(method, frames,
                    buildFrame.getStack(buildFrame.getStackSize() - 1), new HashSet<>()) != created) { return false; }
            boolean posted = false, returned = false;
            for (var instruction : method.instructions) {
                var at = frames[method.instructions.indexOf(instruction)];
                if (at == null) { continue; }
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/neoforged/bus/api/IEventBus")
                        && call.name.equals("post") && Type.getArgumentTypes(call.desc).length == 1
                        && creation(method, frames, at.getStack(at.getStackSize() - 1), new HashSet<>()) == created) { posted = true; }
                if (instruction.getOpcode() == ARETURN && origin(method, frames,
                        at.getStack(at.getStackSize() - 1), -1, build, new HashSet<>())) { returned = true; }
            }
            if (!posted || !returned) { return false; }
            if (frame == null || !origin(method, frames, frame.getStack(frame.getStackSize() - 2), 0, null, new HashSet<>())) { return false; }
            if (!origin(method, frames, frame.getStack(frame.getStackSize() - 1), 1, transformed ? router : null, new HashSet<>())) { return false; }
            if (!transformed) { return true; }
            MethodNode handler = method(node, router.name, router.desc);
            if (handler == null || !owned(handler)) { return false; }
            if (!adapterFlow(node, handler)) { return false; }
            var routeFrame = frames[method.instructions.indexOf(router)];
            return routeFrame != null
                    && origin(method, frames, routeFrame.getStack(routeFrame.getStackSize() - 2), 0, null, new HashSet<>())
                    && origin(method, frames, routeFrame.getStack(routeFrame.getStackSize() - 1), 1, null, new HashSet<>());
        } catch (AnalyzerException | RuntimeException incompatible) { return false; }
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static boolean owned(MethodNode method) {
        return method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(annotation ->
                annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                        && annotation.values != null && annotation.values.contains(MIXIN));
    }

    private static boolean adapterFlow(ClassNode owner, MethodNode handler) throws AnalyzerException {
        var frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, handler);
        for (var instruction : handler.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(
                    "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireAttributesCompatibility")
                    || !call.name.equals("project") || !call.desc.equals(DESC)) { continue; }
            var frame = frames[handler.instructions.indexOf(call)];
            if (frame == null || !origin(handler, frames, frame.getStack(frame.getStackSize() - 2), 0, null, new HashSet<>())
                    || !origin(handler, frames, frame.getStack(frame.getStackSize() - 1), 1, null, new HashSet<>())) { return false; }
            for (var returned : handler.instructions) {
                var at = frames[handler.instructions.indexOf(returned)];
                if (returned.getOpcode() != ARETURN || at == null) { continue; }
                var result = at.getStack(at.getStackSize() - 1);
                // Ternary getter guards merge adapter and unchanged-default branches here.
                for (var producer : result.insns) {
                    if (origin(handler, frames, new SourceValue(result.size, producer), -1, call, new HashSet<>())) { return true; }
                }
            }
        }
        return false;
    }

    private static AbstractInsnNode creation(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                            Set<AbstractInsnNode> seen) {
        if (value == null || value.insns.size() != 1) { return null; }
        var source = value.insns.iterator().next();
        if (!seen.add(source)) { return null; }
        var frame = frames[method.instructions.indexOf(source)];
        if (source instanceof TypeInsnNode type && source.getOpcode() == NEW && type.desc.equals(EVENT)) { return source; }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return creation(method, frames, frame.getLocal(variable.var), seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == DUP || source.getOpcode() == CHECKCAST) {
            return creation(method, frames, frame.getStack(frame.getStackSize() - 1), seen);
        }
        return null;
    }

    private static boolean origin(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                  int parameter, MethodInsnNode required, Set<AbstractInsnNode> seen) {
        if (value == null || value.insns.size() != 1) { return false; } // Ambiguous/control-flow merges fail closed.
        var source = value.insns.iterator().next();
        if (!seen.add(source)) { return false; }
        var frame = frames[method.instructions.indexOf(source)];
        if (source == required) { return true; }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            var local = frame.getLocal(variable.var);
            return required == null && variable.var == parameter && local.insns.isEmpty()
                    || origin(method, frames, local, parameter, required, seen);
        }
        if (source instanceof VarInsnNode && source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return origin(method, frames, frame.getStack(frame.getStackSize() - 1), parameter, required, seen);
        }
        if (source instanceof MethodInsnNode call && Type.getReturnType(call.desc).getDescriptor().equals(MODIFIERS)) {
            Type[] args = Type.getArgumentTypes(call.desc);
            for (int i = 0; i < args.length; i++) {
                if (args[i].getDescriptor().equals(MODIFIERS)
                        && origin(method, frames, frame.getStack(frame.getStackSize() - args.length + i), parameter, required, new HashSet<>(seen))) { return true; }
            }
        }
        return false;
    }
}
