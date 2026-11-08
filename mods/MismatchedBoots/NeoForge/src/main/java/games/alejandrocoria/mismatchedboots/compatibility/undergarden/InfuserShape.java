package games.alejandrocoria.mismatchedboots.compatibility.undergarden;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Extend only proven native recipe/menu operands, after final peer boolean chains. */
final class InfuserShape implements Opcodes {
    static final String RECIPE = "quek/undergarden/recipe/InfusingRecipe";
    static final String MENU = "quek/undergarden/inventory/InfuserMenu";
    static final String INPUT = "quek/undergarden/recipe/InfuserInput";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String INGREDIENT = "net/minecraft/world/item/crafting/Ingredient";
    static final String SET = "net/minecraft/world/item/crafting/RecipePropertySet";
    static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/undergarden/InfuserCompatibility";

    private InfuserShape() {}

    static boolean apply(ClassNode owner) throws AnalyzerException {
        boolean recipe = owner.name.equals(RECIPE);
        if (!recipe && !owner.name.equals(MENU)) {
            return false;
        }
        String descriptor = recipe ? "(L" + INPUT + ";Lnet/minecraft/world/level/Level;)Z" : "(L" + STACK + ";)Z";
        var methods = owner.methods.stream().filter(m -> m.name.equals(recipe ? "matches" : "canInfuse")
                && m.desc.equals(descriptor) && (m.access & ACC_STATIC) == 0).toList();
        if (methods.size() != 1) {
            return false;
        }
        var method = methods.getFirst();
        for (var n : method.instructions) {
            if (n instanceof MethodInsnNode call && call.owner.equals(ADAPTER)) {
                return false;
            }
        }
        var flow = new Flow(owner, method);
        var sites = new ArrayList<Site>();
        for (var n : method.instructions) {
            if (recipe ? n.getOpcode() != IFEQ : n.getOpcode() != IRETURN) {
                continue;
            }
            var query = flow.query(flow.argument(n, 0, 1), recipe ? INGREDIENT : SET, "test", "(L" + STACK + ";)Z");
            if (query == null) {
                continue;
            }
            var receiver = flow.origin(flow.argument(query, 0, 2));
            var stack = flow.argument(query, 1, 2);
            if (recipe) {
                if (!call(receiver, RECIPE, "input", "()L" + INGREDIENT + ";")
                        || !flow.parameter(flow.argument(receiver, 0, 1), 0)) {
                    continue;
                }
                var item = flow.origin(stack);
                if (!call(item, INPUT, "item", "()L" + STACK + ";")
                        || !flow.parameter(flow.argument(item, 0, 1), 1)
                        || !slotGate(flow, (JumpInsnNode) n)) {
                    continue;
                }
            } else if (!(receiver instanceof FieldInsnNode field) || field.getOpcode() != GETFIELD
                    || !field.owner.equals(MENU) || !field.name.equals("acceptedInputs") || !field.desc.equals("L" + SET + ";")
                    || !flow.parameter(flow.argument(field, 0, 1), 0) || !flow.parameter(stack, 1)) {
                continue;
            }
            sites.add(new Site(query, n));
        }
        if (sites.size() != 1) {
            return false;
        }

        var site = sites.getFirst();
        int receiverLocal = method.maxLocals++;
        int stackLocal = method.maxLocals++;
        var capture = new InsnList();
        capture.add(new InsnNode(DUP2));
        capture.add(new VarInsnNode(ASTORE, stackLocal));
        capture.add(new VarInsnNode(ASTORE, receiverLocal));
        method.instructions.insertBefore(site.query, capture);

        var extension = new InsnList();
        if (recipe) {
            extension.add(new VarInsnNode(ALOAD, 0));
            extension.add(new VarInsnNode(ALOAD, receiverLocal));
        }
        extension.add(new VarInsnNode(ALOAD, stackLocal));
        extension.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, recipe ? "recipe" : "menu",
                recipe ? "(ZLjava/lang/Object;L" + INGREDIENT + ";L" + STACK + ";)Z" : "(ZL" + STACK + ";)Z", false));
        method.instructions.insertBefore(site.consumer, extension);
        method.maxStack += 3;
        return true;
    }

    private static boolean slotGate(Flow flow, JumpInsnNode ingredientBranch) {
        // Both failed predicates must converge on the same native false branch.
        for (var n = ingredientBranch.getNext(); n != null && n != ingredientBranch.label; n = n.getNext()) {
            if (!(n instanceof JumpInsnNode branch) || branch.getOpcode() != IFEQ || branch.label != ingredientBranch.label) {
                continue;
            }
            String slot = RECIPE + "$SlotType";
            var equals = flow.query(flow.argument(branch, 0, 1), slot, "equals", "(Ljava/lang/Object;)Z");
            if (equals == null) {
                continue;
            }
            var expected = flow.origin(flow.argument(equals, 0, 2));
            var actual = flow.origin(flow.argument(equals, 1, 2));
            if (call(expected, RECIPE, "getRecipeSlotType", "()L" + slot + ";")
                    && flow.parameter(flow.argument(expected, 0, 1), 0)
                    && call(actual, INPUT, "type", "()L" + slot + ";")
                    && flow.parameter(flow.argument(actual, 0, 1), 1)) {
                return true;
            }
        }
        return false;
    }

    private record Site(MethodInsnNode query, AbstractInsnNode consumer) {}

    private static boolean call(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc);
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

        SourceValue argument(AbstractInsnNode n, int index, int count) {
            var frame = frames[method.instructions.indexOf(n)];
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
                var local = frames[method.instructions.indexOf(n)].getLocal(load.var);
                return local.insns.isEmpty() ? n : origin(local, seen);
            }
            if (n instanceof VarInsnNode && n.getOpcode() >= ISTORE && n.getOpcode() <= ASTORE || n.getOpcode() == CHECKCAST) {
                return origin(argument(n, 0, 1), seen);
            }
            return n;
        }

        boolean parameter(SourceValue value, int local) {
            var n = origin(value);
            return n instanceof VarInsnNode load && load.var == local
                    && frames[method.instructions.indexOf(n)].getLocal(local).insns.isEmpty();
        }

        MethodInsnNode query(SourceValue value, String owner, String name, String desc) {
            var n = origin(value);
            var seen = new HashSet<AbstractInsnNode>();
            while (n instanceof MethodInsnNode peer && seen.add(n)) {
                if (call(peer, owner, name, desc)) {
                    return peer;
                }
                var args = Type.getArgumentTypes(peer.desc);
                if (args.length == 0 || !args[0].equals(Type.BOOLEAN_TYPE) || !Type.getReturnType(peer.desc).equals(Type.BOOLEAN_TYPE)) {
                    return null;
                }
                n = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1,
                        args.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
            }
            return null;
        }
    }
}
