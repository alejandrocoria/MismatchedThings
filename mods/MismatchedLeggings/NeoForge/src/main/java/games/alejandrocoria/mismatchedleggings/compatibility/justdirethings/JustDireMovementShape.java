package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
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

/** Local callback anchors, not a fingerprint of unrelated native methods or harmless additions. */
public final class JustDireMovementShape implements Opcodes {
    static final String OWNER = "com/direwolf20/justdirethings/common/items/interfaces/AbilityMethods";
    static final String GETTER = "mismatchedleggings$jdtMovementReady";

    private JustDireMovementShape() {}

    static boolean entry(ClassNode node) {
        for (String name : List.of("walkSpeed", "runSpeed", "swimSpeed")) {
            var matches = node.methods.stream().filter(method -> method.name.equals(name)
                    && method.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z")
                    && (method.access & ACC_STATIC) != 0).toList();
            if (matches.size() != 1) { return false; }
            var method = matches.getFirst();
            Frame<SourceValue>[] frames;
            try { frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, method); }
            catch (AnalyzerException | RuntimeException incompatible) { return false; }
            boolean value = false;
            boolean motion = false;
            for (var instruction : matches.getFirst().instructions) {
                if (!(instruction instanceof MethodInsnNode call)) { continue; }
                if (call.owner.equals("com/direwolf20/justdirethings/common/items/interfaces/ToggleableTool")
                        && call.name.equals("getToolValue") && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)I")) {
                    var frame = frames[method.instructions.indexOf(call)];
                    value |= frame != null && parameter(method, frames, frame.getStack(frame.getStackSize() - 2), 2, new HashSet<>());
                }
                if (call.owner.equals("net/minecraft/world/entity/player/Player") && call.name.equals("moveRelative")
                        && call.desc.equals("(FLnet/minecraft/world/phys/Vec3;)V")) {
                    var frame = frames[method.instructions.indexOf(call)];
                    motion |= frame != null && parameter(method, frames, frame.getStack(frame.getStackSize() - 3), 1, new HashSet<>());
                }
            }
            if (!value || !motion) { return false; }
        }
        return true;
    }

    private static boolean parameter(MethodNode method, Frame<SourceValue>[] frames, SourceValue value,
                                     int index, Set<AbstractInsnNode> seen) {
        if (value == null || value.insns.size() != 1) { return false; }
        var source = value.insns.iterator().next();
        if (!seen.add(source)) { return false; }
        var frame = frames[method.instructions.indexOf(source)];
        if (frame == null) { return false; }
        if (source instanceof VarInsnNode variable && source.getOpcode() == ALOAD) {
            var local = frame.getLocal(variable.var);
            return variable.var == index && local.insns.isEmpty() || parameter(method, frames, local, index, seen);
        }
        if (source.getOpcode() == ASTORE || source.getOpcode() == CHECKCAST) {
            return parameter(method, frames, frame.getStack(frame.getStackSize() - 1), index, seen);
        }
        return false;
    }

    static boolean publish(ClassNode node) {
        var getter = node.methods.stream().filter(method -> method.name.equals(GETTER) && method.desc.equals("()Z")).findFirst().orElse(null);
        if (getter == null) { return false; }
        var real = new ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
        getter.instructions.forEach(instruction -> { if (instruction.getOpcode() >= 0) { real.add(instruction); } });
        boolean intact = getter.visibleAnnotations != null && getter.visibleAnnotations.stream().anyMatch(annotation ->
                annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null
                        && annotation.values.contains("games.alejandrocoria.mismatchedleggings.mixin.compatibility.justdirethings.JustDireMovementMixin"))
                && real.size() == 2 && real.getFirst().getOpcode() == ICONST_0 && real.getLast().getOpcode() == IRETURN;
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(intact && entry(node) ? ICONST_1 : ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
        getter.tryCatchBlocks.clear();
        getter.maxStack = 1;
        return intact && entry(node);
    }
}
