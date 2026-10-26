package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
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

/** Prove the local FEET -> Cloggrum -> no-Gooey return, not the whole goo handler. */
final class UndergardenGooShape implements Opcodes {
    static final String TARGET = "quek/undergarden/block/GooLayerBlock";
    static final String METHOD = "entityInside";
    static final String DESCRIPTOR = "(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V";
    static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/undergarden/UndergardenBoots";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    private static final String HOLDER = "(Lnet/minecraft/core/Holder;)Z";

    private UndergardenGooShape() {
    }

    static boolean apply(ClassNode owner) throws AnalyzerException {
        if (!owner.name.equals(TARGET)) {
            return false;
        }

        var methods = owner.methods.stream()
                                   .filter(method -> method.name.equals(METHOD) && method.desc.equals(DESCRIPTOR))
                                   .toList();
        if (methods.size() != 1 || (methods.getFirst().access & ACC_STATIC) != 0) {
            return false;
        }

        var method = methods.getFirst();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(ADAPTER)) {
                return false;
            }
        }

        var flow = new Flow(owner.name, method);
        var sites = new ArrayList<Site>();
        for (var instruction : method.instructions) {
            if (!(instruction instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ) {
                continue;
            }

            // Match the Cloggrum query and trace its stack back to this player's FEET slot.
            var query = flow.query(flow.argument(branch, 0, 1), STACK, "is");
            if (query == null || !field(flow.origin(flow.argument(query, 1, 2)),
                                        "quek/undergarden/registry/UGItems", "CLOGGRUM_BOOTS",
                                        "Lnet/neoforged/neoforge/registries/DeferredItem;")) {
                continue;
            }

            var item = flow.origin(flow.argument(query, 0, 2));
            if (!call(item, "net/minecraft/world/entity/player/Inventory", "getItem", "(I)L" + STACK + ";")) {
                continue;
            }

            var inventory = flow.origin(flow.argument(item, 0, 2));
            var index = flow.origin(flow.argument(item, 1, 2));
            if (!call(inventory, PLAYER, "getInventory", "()Lnet/minecraft/world/entity/player/Inventory;")
                    || !flow.parameter(flow.argument(inventory, 0, 1), 4)
                    || !call(index, SLOT, "getIndex", "(I)I")
                    || !field(flow.origin(flow.argument(index, 0, 2)), SLOT, "FEET", "L" + SLOT + ";")
                    || !(flow.origin(flow.argument(index, 1, 2)) instanceof IntInsnNode base) || base.operand != 36) {
                continue;
            }

            // The matching branch must return only when the player lacks Gooey.
            boolean noGooeyReturn = false;
            for (var next = branch.getNext(); next != null && next != branch.label; next = next.getNext()) {
                if (!(next instanceof JumpInsnNode effect) || effect.getOpcode() != IFNE || effect.label != branch.label) {
                    continue;
                }

                var hasEffect = flow.query(flow.argument(effect, 0, 1), PLAYER, "hasEffect");
                var exit = effect.getNext();
                while (exit != null && (exit.getOpcode() < 0 || exit.getOpcode() == NOP)) {
                    exit = exit.getNext();
                }

                if (hasEffect != null && flow.parameter(flow.argument(hasEffect, 0, 2), 4)
                        && field(flow.origin(flow.argument(hasEffect, 1, 2)), "quek/undergarden/registry/UGEffects",
                                 "GOOEY", "Lnet/neoforged/neoforge/registries/DeferredHolder;")
                        && exit != null && exit.getOpcode() == RETURN) {
                    noGooeyReturn = true;
                }
            }

            if (noGooeyReturn) {
                sites.add(new Site(query, branch));
            }
        }

        if (sites.size() != 1) {
            return false;
        }

        var site = sites.getFirst();
        // Capture the final query operands, then extend the final boolean consumer after peer result chains.
        int stackLocal = method.maxLocals++;
        int holderLocal = method.maxLocals++;
        var save = new InsnList();
        save.add(new InsnNode(DUP2));
        save.add(new VarInsnNode(ASTORE, holderLocal));
        save.add(new VarInsnNode(ASTORE, stackLocal));
        method.instructions.insertBefore(site.query, save);

        var extend = new InsnList();
        extend.add(new VarInsnNode(ALOAD, stackLocal));
        extend.add(new VarInsnNode(ALOAD, holderLocal));
        extend.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "goo", "(ZL" + STACK + ";Lnet/minecraft/core/Holder;)Z", false));
        method.instructions.insertBefore(site.branch, extend);
        method.maxStack += 2;

        return true;
    }

    private record Site(MethodInsnNode query, JumpInsnNode branch) {
    }

    private static final class Flow {
        private final MethodNode method;
        private final Frame<SourceValue>[] frames;

        Flow(String owner, MethodNode method) throws AnalyzerException {
            this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override
                public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                    return instruction.getOpcode() >= DUP && instruction.getOpcode() <= SWAP
                            ? value : super.copyOperation(instruction, value);
                }
            }).analyzeAndComputeMaxs(owner, method);
        }

        SourceValue argument(AbstractInsnNode instruction, int index, int operandCount) {
            var frame = frames[method.instructions.indexOf(instruction)];
            return frame == null || frame.getStackSize() < operandCount
                    ? null : frame.getStack(frame.getStackSize() - operandCount + index);
        }

        AbstractInsnNode origin(SourceValue value) {
            return origin(value, new HashSet<>());
        }

        private AbstractInsnNode origin(SourceValue value, Set<AbstractInsnNode> seen) {
            if (value == null || value.insns.size() != 1) {
                return null;
            }

            var instruction = value.insns.iterator().next();
            if (!seen.add(instruction)) {
                return null;
            }

            if (instruction instanceof VarInsnNode variable && instruction.getOpcode() >= ILOAD && instruction.getOpcode() <= ALOAD) {
                var local = frames[method.instructions.indexOf(instruction)].getLocal(variable.var);
                return local.insns.isEmpty() ? instruction : origin(local, seen);
            }

            if ((instruction instanceof VarInsnNode && instruction.getOpcode() >= ISTORE && instruction.getOpcode() <= ASTORE)
                    || instruction.getOpcode() == CHECKCAST) {
                return origin(argument(instruction, 0, 1), seen);
            }

            return instruction;
        }

        boolean parameter(SourceValue value, int local) {
            var instruction = origin(value);
            return instruction instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == local
                    && frames[method.instructions.indexOf(instruction)].getLocal(local).insns.isEmpty();
        }

        MethodInsnNode query(SourceValue value, String owner, String name) {
            var instruction = origin(value);
            var seen = new HashSet<AbstractInsnNode>();
            while (instruction instanceof MethodInsnNode peer && seen.add(instruction)) {
                if (call(peer, owner, name, HOLDER)) {
                    return peer;
                }

                // Follow wrappers that consume and return the query's boolean without repeating the query.
                var arguments = Type.getArgumentTypes(peer.desc);
                if (arguments.length == 0 || !arguments[0].equals(Type.BOOLEAN_TYPE)
                        || !Type.getReturnType(peer.desc).equals(Type.BOOLEAN_TYPE)) {
                    return null;
                }

                instruction = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1,
                                              arguments.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
            }

            return null;
        }
    }

    private static boolean call(AbstractInsnNode instruction, String owner, String name, String descriptor) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKEVIRTUAL
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static boolean field(AbstractInsnNode instruction, String owner, String name, String descriptor) {
        return instruction instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC
                && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor);
    }
}
