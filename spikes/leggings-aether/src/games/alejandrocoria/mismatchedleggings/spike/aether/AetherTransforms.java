package games.alejandrocoria.mismatchedleggings.spike.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Feasibility edits, not production guards. Production must prove the selected native consumer. */
public final class AetherTransforms implements Opcodes {
    static final String ADAPTER = "games/alejandrocoria/mismatchedleggings/spike/aether/AetherAdapters";
    private static final String STACK = "net/minecraft/world/item/ItemStack";

    private AetherTransforms() {}

    public static void apply(ClassNode node) {
        boolean sets = node.name.endsWith("/EquipmentUtil");
        boolean armor = node.name.endsWith("/AetherIIEventListeners");
        var method = node.methods.stream().filter(m -> m.name.equals(sets ? "getArmorCount" : armor ? "onArmorDamaged" : "entityInside"))
                .findFirst().orElseThrow();
        int edits = 0;
        for (var instruction : method.instructions.toArray()) {
            if (sets && instruction.getOpcode() == IRETURN) {
                var hook = new InsnList();
                hook.add(new VarInsnNode(ALOAD, 0));
                hook.add(new VarInsnNode(ALOAD, 1));
                hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "count",
                        "(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/tags/TagKey;)I", false));
                method.instructions.insertBefore(instruction, hook);
                edits++;
            } else if (!sets && instruction instanceof MethodInsnNode call && call.owner.equals(STACK)
                    && call.name.equals("has") && call.desc.equals("(Ljava/util/function/Supplier;)Z")) {
                int stack = method.maxLocals++;
                int supplier = method.maxLocals++;
                var save = new InsnList();
                save.add(new InsnNode(DUP2));
                save.add(new VarInsnNode(ASTORE, supplier));
                save.add(new VarInsnNode(ASTORE, stack));
                method.instructions.insertBefore(call, save);
                var hook = new InsnList();
                hook.add(new VarInsnNode(ALOAD, stack));
                hook.add(new VarInsnNode(ALOAD, supplier));
                hook.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "tier",
                        "(ZL" + STACK + ";Ljava/util/function/Supplier;)Z", false));
                method.instructions.insert(call, hook);
                edits++;
            }
        }
        if (edits != (sets || armor ? 1 : 3)) {
            throw new IllegalStateException("Spike native contract changed: " + node.name + " edits=" + edits);
        }
        method.maxStack += 4;
        if (armor) {
            presentation(node);
        }
    }

    static void presentation(ClassNode node) {
        for (var method : node.methods) {
            if (!method.name.equals("onAddAttributeTooltips") && !method.name.equals("onAddTooltipsLowest")) {
                continue;
            }
            int edits = 0;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("com/aetherteam/aetherii/client/event/hooks/RenderHooks")
                        || !(call.name.equals("addAbilityAttributeTooltip") || call.name.equals("addReinforcementTooltip"))) {
                    continue;
                }
                int count = org.objectweb.asm.Type.getArgumentTypes(call.desc).length;
                int first = method.maxLocals;
                method.maxLocals += count;
                var skip = new LabelNode();
                var condition = new InsnList();
                for (int arg = count - 1; arg >= 0; arg--) { condition.add(new VarInsnNode(ASTORE, first + arg)); }
                condition.add(new VarInsnNode(ALOAD, first));
                condition.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "managed", "(L" + STACK + ";)Z", false));
                condition.add(new JumpInsnNode(IFNE, skip));
                for (int arg = 0; arg < count; arg++) { condition.add(new VarInsnNode(ALOAD, first + arg)); }
                method.instructions.insertBefore(call, condition);
                method.instructions.insert(call, skip);
                method.maxStack = Math.max(method.maxStack, count);
                edits++;
            }
            if (edits != 1) { throw new IllegalStateException("Spike presentation producer changed: " + method.name); }
        }
    }
}
