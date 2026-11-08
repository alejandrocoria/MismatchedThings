package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.HashSet;
import java.util.Set;

/** Local native operand provenance; deliberately scoped to this Aether integration. */
final class AetherFlow implements Opcodes {
    final ClassNode owner;
    final MethodNode method;
    private final Frame<SourceValue>[] frames;

    AetherFlow(ClassNode owner, MethodNode method) throws AnalyzerException {
        this.owner = owner;
        this.method = method;
        frames = new Analyzer<>(new SourceInterpreter(ASM9) {
            @Override
            public SourceValue copyOperation(AbstractInsnNode n, SourceValue v) {
                return n.getOpcode() >= DUP && n.getOpcode() <= SWAP ? v : super.copyOperation(n, v);
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

    AbstractInsnNode origin(SourceValue v) {
        return origin(v, new HashSet<>());
    }

    private AbstractInsnNode origin(SourceValue v, Set<AbstractInsnNode> seen) {
        if (v == null || v.insns.size() != 1) {
            return null;
        }

        var n = v.insns.iterator().next();
        if (!seen.add(n)) {
            return null;
        }
        if (n instanceof VarInsnNode load && n.getOpcode() >= ILOAD && n.getOpcode() <= ALOAD) {
            var old = at(n).getLocal(load.var);
            return old.insns.isEmpty() ? n : origin(old, seen);
        }
        if (n instanceof VarInsnNode && n.getOpcode() >= ISTORE && n.getOpcode() <= ASTORE || n.getOpcode() == CHECKCAST) {
            return origin(argument(n, 0, 1), seen);
        }

        return n;
    }

    AbstractInsnNode base(SourceValue v) throws AnalyzerException {
        var n = origin(v);
        var seen = new HashSet<AbstractInsnNode>();
        while (n instanceof MethodInsnNode peer && peer.owner.equals(owner.name) && seen.add(n)) {
            var args = Type.getArgumentTypes(peer.desc);
            if (args.length == 0 || !args[0].equals(Type.getReturnType(peer.desc))) {
                break;
            }

            var body = method(owner, peer.name, peer.desc);
            if (body == null || body == method) {
                break;
            }

            var proof = new AetherFlow(owner, body);
            boolean returned = false;
            boolean identity = true;
            for (var ret : body.instructions) {
                if (ret.getOpcode() == ARETURN) {
                    returned = true;
                    identity &= proof.parameter(proof.argument(ret, 0, 1), (body.access & ACC_STATIC) == 0 ? 1 : 0);
                }
            }
            if (!returned || !identity) {
                break;
            }
            n = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1, args.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
        }

        return n;
    }

    boolean parameter(SourceValue v, int local) {
        var n = origin(v);
        return n instanceof VarInsnNode load && load.var == local && at(n).getLocal(local).insns.isEmpty();
    }

    boolean same(SourceValue a, SourceValue b) throws AnalyzerException {
        var n = base(a);
        return n != null && n == base(b);
    }

    MethodInsnNode query(SourceValue v) {
        return booleanQuery(v, "has", "(Ljava/util/function/Supplier;)Z");
    }

    MethodInsnNode booleanQuery(SourceValue v, String name, String descriptor) {
        var n = origin(v);
        var seen = new HashSet<AbstractInsnNode>();
        while (n instanceof MethodInsnNode peer && seen.add(n)) {
            if (call(peer, "net/minecraft/world/item/ItemStack", name, descriptor)) {
                return peer;
            }
            var args = Type.getArgumentTypes(peer.desc);
            if (args.length == 0 || !args[0].equals(Type.BOOLEAN_TYPE) || !Type.getReturnType(peer.desc).equals(Type.BOOLEAN_TYPE)) {
                return null;
            }
            n = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1, args.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
        }
        return null;
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        var found = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static boolean call(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc);
    }
}

