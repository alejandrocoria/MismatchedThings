package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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
import java.util.Set;

/** Local equipment-view → native clock/item/phase-parameter flow, not a renderer fingerprint. */
public final class JustDireCooldownHudShape implements Opcodes {
    public static final String HUD = "com/direwolf20/justdirethings/client/overlays/AbilityCooldownOverlay";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String SLOT = "Lnet/minecraft/world/entity/EquipmentSlot;";
    private static final String TOOL = "com/direwolf20/justdirethings/common/items/interfaces/ToggleableTool";
    private static final String PARAMS = "com/direwolf20/justdirethings/common/items/interfaces/AbilityParams";
    private static final String RENDER = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.compatibility.justdirethings.JustDireCooldownHudMixin";
    private static final String READY = "mismatchedboots$justDireCooldownReady";
    private static final String VIEW = "games/alejandrocoria/mismatchedboots/client/compatibility/justdirethings/JustDireCooldownHud";

    private JustDireCooldownHudShape() {
    }

    static boolean entry(ClassNode node) {
        MethodNode render = method(node, "render", RENDER);
        if (render == null || (render.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0) {
            return false;
        }
        MethodInsnNode read = null;
        for (var instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/player/Player")
                    && call.name.equals("getItemBySlot") && call.desc.equals("(" + SLOT + ")" + STACK)) {
                if (read != null) {
                    return false;
                }
                read = call;
            }
        }
        return read != null && flow(node, render, read);
    }

    public static boolean publish(ClassNode node) {
        MethodNode ready = method(node, READY, "()Z");
        if (ready == null) {
            return false;
        }
        var code = new ArrayList<AbstractInsnNode>();
        for (var instruction : ready.instructions) {
            if (instruction.getOpcode() >= 0) {
                code.add(instruction);
            }
        }
        boolean intact = owned(ready) && ready.access == (ACC_PRIVATE | ACC_STATIC) && code.size() == 2
                && code.getFirst().getOpcode() == ICONST_0 && code.getLast().getOpcode() == IRETURN;
        ready.instructions.clear();
        ready.instructions.add(new InsnNode(ICONST_0));
        ready.instructions.add(new InsnNode(IRETURN));
        ready.tryCatchBlocks.clear();
        ready.maxStack = 1;
        if (!intact) {
            return false;
        }
        MethodNode render = method(node, "render", RENDER);
        if (render == null) {
            return false;
        }
        MethodInsnNode route = null;
        for (var instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                    && call.name.contains("mismatchedboots$justDireCooldownView") && call.desc.equals("(" + STACK + SLOT + ")" + STACK)) {
                if (route != null) {
                    return false;
                }
                route = call;
            }
        }
        if (route == null || !equipmentArgument(node, render, route)
                || !adapter(node, method(node, route.name, route.desc)) || !flow(node, render, route)) {
            return false;
        }
        ready.instructions.set(ready.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    private static boolean flow(ClassNode node, MethodNode render, MethodInsnNode source) {
        try {
            var frames = frames(node, render);
            if (frames[render.instructions.indexOf(source)] == null) {
                return false;
            }
            boolean clocks = false, item = false, active = false, cooldown = false;
            for (var instruction : render.instructions) {
                var frame = frames[render.instructions.indexOf(instruction)];
                if (frame == null) {
                    continue;
                }
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack")) {
                    if (call.name.equals("getItem") && call.desc.equals("()Lnet/minecraft/world/item/Item;")) {
                        item |= from(render, frames, frame.getStack(frame.getStackSize() - 1), source, new HashSet<>());
                    }
                    if (call.name.equals("get") && call.desc.equals("(Ljava/util/function/Supplier;)Ljava/lang/Object;")) {
                        clocks |= from(render, frames, frame.getStack(frame.getStackSize() - 2), source, new HashSet<>())
                                && field(render,
                                         frames,
                                         frame.getStack(frame.getStackSize() - 1),
                                         "com/direwolf20/justdirethings/common/items/datacomponents/JustDireDataComponents",
                                         "ABILITY_COOLDOWNS",
                                         new HashSet<>());
                    }
                }
                if (instruction instanceof FieldInsnNode phase && phase.getOpcode() == GETFIELD && phase.owner.equals(PARAMS)
                        && phase.desc.equals("I") && Set.of("activeCooldown", "cooldown").contains(phase.name)) {
                    MethodInsnNode parameters = producer(render, frames, frame.getStack(frame.getStackSize() - 1), new HashSet<>());
                    if (parameters == null || !parameters.owner.equals(TOOL) || !parameters.name.equals("getAbilityParams")
                            || !parameters.desc.equals("(Lcom/direwolf20/justdirethings/common/items/interfaces/Ability;)L" + PARAMS + ";")) {
                        return false;
                    }
                    var at = frames[render.instructions.indexOf(parameters)];
                    if (at == null || !from(render, frames, at.getStack(at.getStackSize() - 2), source, new HashSet<>())) {
                        return false;
                    }
                    active |= phase.name.equals("activeCooldown");
                    cooldown |= phase.name.equals("cooldown");
                }
            }
            return clocks && item && active && cooldown;
        } catch (AnalyzerException | RuntimeException incompatible) {
            return false;
        }
    }

    private static boolean adapter(ClassNode node, MethodNode handler) {
        if (handler == null || !owned(handler)) {
            return false;
        }
        try {
            var frames = frames(node, handler);
            MethodInsnNode view = null;
            MethodInsnNode guard = null;
            for (var instruction : handler.instructions) {
                if (instruction instanceof MethodInsnNode call && frames[handler.instructions.indexOf(call)] != null) {
                    if (call.owner.equals(node.name) && call.name.equals(READY) && call.desc.equals("()Z")) {
                        guard = call;
                    }
                    if (call.owner.equals(VIEW) && call.name.equals("view") && call.desc.equals("(" + STACK + SLOT + ")" + STACK)) {
                        if (view != null) {
                            return false;
                        }
                        view = call;
                        var frame = frames[handler.instructions.indexOf(call)];
                        if (!parameter(handler, frames, frame.getStack(frame.getStackSize() - 2), 1, new HashSet<>())
                                || !parameter(handler, frames, frame.getStack(frame.getStackSize() - 1), 2, new HashSet<>())) {
                            return false;
                        }
                    }
                }
            }
            if (guard == null || view == null) {
                return false;
            }
            boolean guarded = false;
            for (var instruction : handler.instructions) {
                if (instruction instanceof JumpInsnNode branch && branch.getOpcode() == IFEQ
                        && handler.instructions.indexOf(branch) < handler.instructions.indexOf(view)
                        && handler.instructions.indexOf(branch.label) > handler.instructions.indexOf(view)) {
                    var frame = frames[handler.instructions.indexOf(branch)];
                    guarded |= frame != null && from(handler, frames, frame.getStack(frame.getStackSize() - 1), guard, new HashSet<>());
                }
            }
            if (!guarded) {
                return false;
            }
            for (var instruction : handler.instructions) {
                var frame = frames[handler.instructions.indexOf(instruction)];
                if (instruction.getOpcode() != ARETURN || frame == null) {
                    continue;
                }
                for (var origin : frame.getStack(frame.getStackSize() - 1).insns) {
                    if (from(handler, frames, new SourceValue(1, origin), view, new HashSet<>())) {
                        return true;
                    }
                }
            }
        } catch (AnalyzerException | RuntimeException incompatible) {
            return false;
        }
        return false;
    }

    private static boolean equipmentArgument(ClassNode node, MethodNode render, MethodInsnNode route) {
        try {
            var frames = frames(node, render);
            var at = frames[render.instructions.indexOf(route)];
            if (at == null) {
                return false;
            }
            var read = producer(render, frames, at.getStack(at.getStackSize() - 2), new HashSet<>());
            if (read == null || !Type.getReturnType(read.desc).getDescriptor().equals(STACK)) {
                return false;
            }
            var arguments = Type.getArgumentTypes(read.desc);
            var readFrame = frames[render.instructions.indexOf(read)];
            if (readFrame == null) {
                return false;
            }
            for (int index = 0; index < arguments.length; index++) {
                if (!arguments[index].getDescriptor().equals(SLOT)) {
                    continue;
                }
                if (terminal(render, frames, readFrame.getStack(readFrame.getStackSize() - arguments.length + index), new HashSet<>())
                        != terminal(render, frames, at.getStack(at.getStackSize() - 1), new HashSet<>())) {
                    return false;
                }
                if (nativeRead(read)) {
                    return true;
                }
                if (!read.owner.equals(node.name)) {
                    return false;
                }
                if (reachesRead(node, method(node, read.name, read.desc), new HashSet<>())) {
                    return true;
                }
                for (int argument = 0; argument < arguments.length; argument++) {
                    if (!arguments[argument].getDescriptor().equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")) {
                        continue;
                    }
                    var operation = terminal(render,
                                             frames,
                                             readFrame.getStack(readFrame.getStackSize() - arguments.length + argument),
                                             new HashSet<>());
                    if (operation instanceof InvokeDynamicInsnNode lambda && lambdaReads(node, lambda, new HashSet<>())) {
                        return true;
                    }
                }
                return false;
            }
        } catch (AnalyzerException | RuntimeException incompatible) {
            return false;
        }
        return false;
    }

    private static boolean nativeRead(MethodInsnNode call) {
        return call.owner.equals("net/minecraft/world/entity/player/Player") && call.name.equals("getItemBySlot")
                && call.desc.equals("(" + SLOT + ")" + STACK);
    }

    private static boolean reachesRead(ClassNode node, MethodNode method, Set<String> seen) {
        if (method == null || !seen.add(method.name + method.desc)) {
            return false;
        }
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && (nativeRead(call)
                    || call.owner.equals(node.name) && reachesRead(node, method(node, call.name, call.desc), seen))) {
                return true;
            }
            // WrapOperation chains carry the original equipment read through their Operation lambda.
            if (instruction instanceof InvokeDynamicInsnNode call) {
                if (lambdaReads(node, call, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean lambdaReads(ClassNode node, InvokeDynamicInsnNode lambda, Set<String> seen) {
        for (Object argument : lambda.bsmArgs) {
            if (argument instanceof Handle handle && handle.getOwner().equals(node.name)
                    && reachesRead(node, method(node, handle.getName(), handle.getDesc()), seen)) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode terminal(MethodNode method, Frame<SourceValue>[] frames, SourceValue value, Set<AbstractInsnNode> seen) {
        AbstractInsnNode source = single(value, seen);
        if (source == null) {
            return null;
        }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) {
            return null;
        }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return terminal(method, frames, frame.getLocal(variable.var), seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return terminal(method, frames, frame.getStack(frame.getStackSize() - 1), seen);
        }
        return source;
    }

    private static Frame<SourceValue>[] frames(ClassNode node, MethodNode method) throws AnalyzerException {
        // Expression injection rearranges receiver/result with SWAP. Retain each value's
        // producer across stack-only permutations instead of conflating them at that opcode.
        var interpreter = new SourceInterpreter(ASM9) {
            @Override
            public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                return instruction.getOpcode() == SWAP || instruction.getOpcode() == DUP ? value : super.copyOperation(instruction, value);
            }
        };
        return new Analyzer<>(interpreter).analyze(node.name, method);
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(desc)).findFirst().orElse(null);
    }

    private static boolean owned(MethodNode method) {
        return method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(annotation ->
                                                                                                        annotation.desc.equals(
                                                                                                                "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
                                                                                                                && annotation.values != null && annotation.values.contains(
                                                                                                                MIXIN));
    }

    private static boolean from(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                MethodInsnNode required, Set<AbstractInsnNode> seen) {
        AbstractInsnNode source = single(value, seen);
        if (source == null) {
            return false;
        }
        if (source == required) {
            return true;
        }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) {
            return false;
        }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return from(method, frames, frame.getLocal(variable.var), required, seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return from(method, frames, frame.getStack(frame.getStackSize() - 1), required, seen);
        }
        if (source instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack") && call.name.equals("getItem")) {
            return from(method, frames, frame.getStack(frame.getStackSize() - 1), required, seen);
        }
        // A surrounding expression transform may return a derived stack; it must still consume our view.
        if (source instanceof MethodInsnNode call && Type.getReturnType(call.desc).getDescriptor().equals(STACK)) {
            var args = Type.getArgumentTypes(call.desc);
            for (int index = 0; index < args.length; index++) {
                if (args[index].getDescriptor().equals(STACK) && from(method,
                                                                      frames,
                                                                      frame.getStack(frame.getStackSize() - args.length + index),
                                                                      required,
                                                                      new HashSet<>(seen))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MethodInsnNode producer(MethodNode method, Frame<SourceValue>[] frames, SourceValue value, Set<AbstractInsnNode> seen) {
        AbstractInsnNode source = single(value, seen);
        if (source instanceof MethodInsnNode call) {
            return call;
        }
        if (source == null) {
            return null;
        }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) {
            return null;
        }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return producer(method, frames, frame.getLocal(variable.var), seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return producer(method, frames, frame.getStack(frame.getStackSize() - 1), seen);
        }
        return null;
    }

    private static boolean parameter(MethodNode method, Frame<SourceValue>[] frames, SourceValue value, int local, Set<AbstractInsnNode> seen) {
        AbstractInsnNode source = single(value, seen);
        if (source == null) {
            return false;
        }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) {
            return false;
        }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return variable.var == local && frame.getLocal(local).insns.isEmpty()
                    || parameter(method, frames, frame.getLocal(variable.var), local, seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return parameter(method, frames, frame.getStack(frame.getStackSize() - 1), local, seen);
        }
        return false;
    }

    private static boolean field(MethodNode method,
                                 Frame<SourceValue>[] frames,
                                 SourceValue value,
                                 String owner,
                                 String name,
                                 Set<AbstractInsnNode> seen) {
        AbstractInsnNode source = single(value, seen);
        if (source instanceof FieldInsnNode field) {
            return field.getOpcode() == GETSTATIC && field.owner.equals(owner) && field.name.equals(name);
        }
        if (source == null) {
            return false;
        }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) {
            return false;
        }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            return field(method, frames, frame.getLocal(variable.var), owner, name, seen);
        }
        return source.getOpcode() == ASTORE && field(method, frames, frame.getStack(frame.getStackSize() - 1), owner, name, seen);
    }

    private static AbstractInsnNode single(SourceValue value, Set<AbstractInsnNode> seen) {
        if (value == null || value.insns.size() != 1) {
            return null;
        }
        var source = value.insns.iterator().next();
        return seen.add(source) ? source : null;
    }
}
