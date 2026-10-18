package games.alejandrocoria.mismatchedboots.compatibility.oritech;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Guard only the native FALL -> equipped FEET -> Exo boolean check, not sound/balance code. */
public final class OritechFallInjectionPoint extends InjectionPoint {
    static final String TARGET = "rearth.oritech.item.tools.armor.ExoArmorItem";
    static final String METHOD = "CancelFallDamage";
    static final String DESCRIPTOR = "(Lnet/minecraft/world/damagesource/DamageSource;"
            + "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;Lnet/minecraft/world/entity/LivingEntity;)Z";
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public OritechFallInjectionPoint(InjectionPointData data) { super(data); }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        MethodInsnNode check = select(descriptor, instructions);
        if (check == null) { warn(); return false; }
        nodes.add(check);
        return true;
    }

    static void warn() {
        if (WARNED.compareAndSet(false, true)) {
            Constants.LOG.warn("Oritech fall-recognition local contract is incompatible; mixed Exo fall protection disabled");
        }
    }

    static MethodInsnNode select(String descriptor, InsnList instructions) {
        if (!DESCRIPTOR.equals(descriptor)) { return null; }
        var code = new ArrayList<AbstractInsnNode>();
        for (var node : instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != Opcodes.NOP) { code.add(node); }
        }
        MethodInsnNode selected = null;
        boolean overwrittenArgument = false;
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i) instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ASTORE
                    && (variable.var == 0 || variable.var == 2)) { overwrittenArgument = true; }
            if (overwrittenArgument || i < 8 || i + 1 >= code.size()
                    || !call(code.get(i), "net/minecraft/world/item/ItemStack", "is", "(Lnet/minecraft/core/Holder;)Z")
                    || !field(code.get(i - 1), "rearth/oritech/init/ToolsContent", "EXO_BOOTS",
                        "Lnet/neoforged/neoforge/registries/DeferredItem;")
                    || !call(code.get(i - 2), "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                        "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
                    || !field(code.get(i - 3), "net/minecraft/world/entity/EquipmentSlot", "FEET",
                        "Lnet/minecraft/world/entity/EquipmentSlot;")
                    || !load(code.get(i - 4), 2)
                    || !(code.get(i - 5) instanceof JumpInsnNode fall) || fall.getOpcode() != Opcodes.IFEQ
                    || !call(code.get(i - 6), "net/minecraft/world/damagesource/DamageSource", "is",
                        "(Lnet/minecraft/resources/ResourceKey;)Z")
                    || !field(code.get(i - 7), "net/minecraft/world/damagesource/DamageTypes", "FALL",
                        "Lnet/minecraft/resources/ResourceKey;")
                    || !load(code.get(i - 8), 0)
                    || !(code.get(i + 1) instanceof JumpInsnNode boots) || boots.getOpcode() != Opcodes.IFEQ
                    || boots.label != fall.label) { continue; }
            if (selected != null) { return null; }
            selected = (MethodInsnNode) code.get(i);
        }
        return selected;
    }

    private static boolean load(AbstractInsnNode node, int argument) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == argument;
    }

    private static boolean field(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && owner.equals(field.owner) && name.equals(field.name) && descriptor.equals(field.desc);
    }

    private static boolean call(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }
}
