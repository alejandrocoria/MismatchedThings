package games.alejandrocoria.mismatchedboots.tooltip;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MixedBootsItemTooltipFlagTest {
    @Test
    void sourceAdapterCapturesTheActualOuterFlagAndForwardsItToTheVirtualHook() throws Exception {
        // Common's frozen vanilla bootstrap cannot construct a new registered item subclass.
        // Guard this precise wiring here; the connected production test checks normal/advanced output.
        var owner = new ClassNode();
        try (var bytes = MixedBootsItem.class.getResourceAsStream("MixedBootsItem.class")) {
            new ClassReader(bytes).accept(owner, 0);
        }
        MethodNode outer = owner.methods.stream().filter(method -> method.name.equals("appendHoverText")).findFirst().orElseThrow();
        var code = code(outer);
        InvokeDynamicInsnNode lambda = code.stream().filter(InvokeDynamicInsnNode.class::isInstance)
                .map(InvokeDynamicInsnNode.class::cast)
                .filter(site -> site.bsmArgs.length > 1 && site.bsmArgs[1] instanceof Handle target
                        && target.getOwner().equals(owner.name)).findFirst().orElseThrow();
        int site = code.indexOf(lambda);
        assertEquals(0, assertInstanceOf(VarInsnNode.class, code.get(site - 2)).var);
        assertEquals(5, assertInstanceOf(VarInsnNode.class, code.get(site - 1)).var, "Capture the actual flag, not a default/global value");
        Handle target = assertInstanceOf(Handle.class, lambda.bsmArgs[1]);
        MethodNode forwarding = owner.methods.stream().filter(method -> method.name.equals(target.getName())
                && method.desc.equals(target.getDesc())).findFirst().orElseThrow();
        var forwardingCode = code(forwarding);
        var invocation = forwardingCode.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(call -> call.name.equals("sourceTooltip")).findFirst().orElseThrow();
        assertEquals(Opcodes.INVOKEVIRTUAL, invocation.getOpcode());
        assertEquals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;)Ljava/util/function/Consumer;", invocation.desc);
        assertEquals(List.of(0, 2, 1, 3), forwardingCode.stream().filter(VarInsnNode.class::isInstance)
                .map(VarInsnNode.class::cast).map(load -> load.var).toList(), "Forward this/source/captured flag/output in that order");
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) { if (instruction.getOpcode() >= 0) { result.add(instruction); } }
        return result;
    }
}
