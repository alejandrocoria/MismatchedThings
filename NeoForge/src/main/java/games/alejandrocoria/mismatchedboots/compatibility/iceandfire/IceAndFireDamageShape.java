package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** Bytecode-only inspection: inspecting an absent optional mod must not load its classes. */
public final class IceAndFireDamageShape {
    static final String HANDLER = "com/iafenvoy/iceandfire/event/handler/ServerEvents";
    static final String SCALE = "com/iafenvoy/iceandfire/item/armor/DragonScaleArmorItem";
    static final String STEEL = "com/iafenvoy/iceandfire/item/armor/DragonSteelArmorItem";
    static final String TROLL = "com/iafenvoy/iceandfire/item/armor/TrollArmorItem";
    static final String ITEMS = "com/iafenvoy/iceandfire/registry/IafItems";
    static final String METHOD = "onEntityDamage";
    static final String LIGHTNING_METHOD = "onLivingHurt";
    static final String DESCRIPTOR =
            "(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V";

    private IceAndFireDamageShape() {
    }

    static boolean isCompatible(ClassNode handler, ClassNode scale, ClassNode steel, ClassNode troll) {
        if (handler == null || !HANDLER.equals(handler.name)
                || !isPublicItemClass(scale, SCALE) || !isPublicItemClass(steel, STEEL)
                || !isPublicItemClass(troll, TROLL)) {
            return false;
        }
        MethodNode method = handler.methods.stream()
                .filter(candidate -> METHOD.equals(candidate.name) && DESCRIPTOR.equals(candidate.desc))
                .findFirst().orElse(null);
        return isPublicHandler(method)
                && feetChecks(method.instructions).size() == 3;
    }

    static boolean isLightningCompatible(ClassNode handler, ClassNode items) {
        if (handler == null || !HANDLER.equals(handler.name) || items == null || !ITEMS.equals(items.name)
                || items.fields.stream().noneMatch(field -> "DRAGONSTEEL_LIGHTNING_BOOTS".equals(field.name)
                    && "Lnet/neoforged/neoforge/registries/DeferredItem;".equals(field.desc)
                    && (field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                        == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))) {
            return false;
        }
        MethodNode method = handler.methods.stream()
                .filter(candidate -> LIGHTNING_METHOD.equals(candidate.name) && DESCRIPTOR.equals(candidate.desc))
                .findFirst().orElse(null);
        return isPublicHandler(method) && lightningFeetChecks(method.instructions).size() == 1;
    }

    private static boolean isPublicHandler(MethodNode method) {
        return method != null && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
    }

    private static boolean isPublicItemClass(ClassNode node, String name) {
        return node != null && name.equals(node.name)
                && "net/minecraft/world/item/Item".equals(node.superName)
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    /** Select one native FEET Troll operation and the Scale/Steel OR; never other-slot checks. */
    public static List<AbstractInsnNode> feetChecks(InsnList instructions) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode node : instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != Opcodes.NOP) {
                code.add(node);
            }
        }
        List<AbstractInsnNode> result = new ArrayList<>();
        for (int index = 4; index < code.size(); index++) {
            if (!(code.get(index) instanceof TypeInsnNode check) || check.getOpcode() != Opcodes.INSTANCEOF
                    || !(TROLL.equals(check.desc) || SCALE.equals(check.desc) || STEEL.equals(check.desc))) {
                continue;
            }
            if (code.get(index - 4) instanceof VarInsnNode entity && entity.getOpcode() == Opcodes.ALOAD
                    && isEventEntity(code, index - 4, entity.var)
                    && code.get(index - 3) instanceof FieldInsnNode slot
                    && slot.getOpcode() == Opcodes.GETSTATIC
                    && "net/minecraft/world/entity/EquipmentSlot".equals(slot.owner)
                    && "FEET".equals(slot.name) && ("L" + slot.owner + ";").equals(slot.desc)
                    && isCall(code.get(index - 2), "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                        "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
                    && isCall(code.get(index - 1), "net/minecraft/world/item/ItemStack", "getItem",
                        "()Lnet/minecraft/world/item/Item;")
                    && hasArmorContribution(code, index, check.desc)) {
                result.add(check);
            }
        }
        if (result.size() != 3
                || List.of(TROLL, SCALE, STEEL).stream().anyMatch(type -> result.stream()
                    .filter(node -> type.equals(((TypeInsnNode) node).desc)).count() != 1)) {
            return List.of();
        }
        var scale = result.stream().filter(node -> SCALE.equals(((TypeInsnNode) node).desc)).findFirst().orElseThrow();
        var steel = result.stream().filter(node -> STEEL.equals(((TypeInsnNode) node).desc)).findFirst().orElseThrow();
        if (nextCode(((JumpInsnNode) nextCode(scale)).label) != nextCode(nextCode(steel))) {
            return List.of(); // The selected FEET pair itself must share one contribution, not another slot's Steel test.
        }
        return List.copyOf(result);
    }

    /** Select the native exact-item predicate, not the equipment read or the three other set checks. */
    public static List<AbstractInsnNode> lightningFeetChecks(InsnList instructions) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode node : instructions) {
            if (node.getOpcode() >= 0 && node.getOpcode() != Opcodes.NOP) {
                code.add(node);
            }
        }
        List<AbstractInsnNode> result = new ArrayList<>();
        for (int index = 6; index < code.size(); index++) {
            if (isCall(code.get(index), "net/minecraft/world/item/ItemStack", "is", "(Ljava/lang/Object;)Z")
                    && code.get(index - 6) instanceof VarInsnNode entity && entity.getOpcode() == Opcodes.ALOAD
                    && isEventEntity(code, index - 6, entity.var)
                    && code.get(index - 5) instanceof FieldInsnNode slot && slot.getOpcode() == Opcodes.GETSTATIC
                    && "net/minecraft/world/entity/EquipmentSlot".equals(slot.owner)
                    && "FEET".equals(slot.name) && ("L" + slot.owner + ";").equals(slot.desc)
                    && isCall(code.get(index - 4), "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                        "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
                    && code.get(index - 3) instanceof FieldInsnNode item && item.getOpcode() == Opcodes.GETSTATIC
                    && ITEMS.equals(item.owner) && "DRAGONSTEEL_LIGHTNING_BOOTS".equals(item.name)
                    && "Lnet/neoforged/neoforge/registries/DeferredItem;".equals(item.desc)
                    && isCall(code.get(index - 2), "net/neoforged/neoforge/registries/DeferredItem", "get",
                        "()Ljava/lang/Object;")
                    && code.get(index - 1) instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                    && "net/minecraft/world/item/Item".equals(cast.desc)
                    && hasZeroDamageConsumer(code, index)) {
                result.add(code.get(index));
            }
        }
        return result.size() == 1 ? List.copyOf(result) : List.of();
    }

    private static boolean isCall(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }

    /** Require direct event-entity assignments, not a hardcoded local ordinal or guessed aliases. */
    private static boolean isEventEntity(List<AbstractInsnNode> code, int read, int local) {
        boolean assigned = false;
        for (int index = 0; index < read; index++) {
            if (code.get(index) instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE && store.var == 0) {
                return false; // Our wrapper receives the original event parameter.
            }
            if (code.get(index) instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE
                    && store.var == local) {
                if (index < 2 || !(code.get(index - 2) instanceof VarInsnNode event) || event.getOpcode() != Opcodes.ALOAD
                        || event.var != 0 || !isCall(code.get(index - 1),
                        "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre", "getEntity",
                        "()Lnet/minecraft/world/entity/LivingEntity;")) {
                    return false;
                }
                assigned = true;
            }
        }
        return assigned;
    }

    /** Validate only the boolean -> one native coefficient subtraction, not the whole damage algorithm. */
    private static boolean hasArmorContribution(List<AbstractInsnNode> code, int predicate, String type) {
        if (predicate + 1 >= code.size() || !(code.get(predicate + 1) instanceof JumpInsnNode branch)) {
            return false;
        }
        int contribution = predicate + 2;
        if (SCALE.equals(type)) {
            if (branch.getOpcode() != Opcodes.IFNE) {
                return false;
            }
            contribution = code.indexOf(nextCode(branch.label));
        } else if (branch.getOpcode() != Opcodes.IFEQ) {
            return false;
        }
        if (contribution < 0 || contribution + 3 >= code.size()
                || !(code.get(contribution) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.FLOAD
                || !(code.get(contribution + 1) instanceof LdcInsnNode amount) || !(amount.cst instanceof Float)
                || code.get(contribution + 2).getOpcode() != Opcodes.FSUB
                || !(code.get(contribution + 3) instanceof VarInsnNode store) || store.getOpcode() != Opcodes.FSTORE
                || store.var != load.var) {
            return false;
        }
        if (SCALE.equals(type)) {
            // Scale and Steel must still converge on the same one-piece contribution.
            return contribution >= 2 && code.get(contribution - 2) instanceof TypeInsnNode steel
                    && steel.getOpcode() == Opcodes.INSTANCEOF && STEEL.equals(steel.desc)
                    && code.get(contribution - 1) instanceof JumpInsnNode steelBranch
                    && steelBranch.getOpcode() == Opcodes.IFEQ
                    && nextCode(steelBranch.label) == nextCode(code.get(contribution + 3));
        }
        return nextCode(branch.label) == nextCode(code.get(contribution + 3));
    }

    private static boolean hasZeroDamageConsumer(List<AbstractInsnNode> code, int predicate) {
        return predicate + 4 < code.size()
                && code.get(predicate + 1) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IFEQ
                && code.get(predicate + 2) instanceof VarInsnNode event && event.getOpcode() == Opcodes.ALOAD
                && event.var == 0 && code.get(predicate + 3).getOpcode() == Opcodes.FCONST_0
                && isCall(code.get(predicate + 4), "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre",
                    "setNewDamage", "(F)V")
                && nextCode(branch.label) == nextCode(code.get(predicate + 4));
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode node) {
        var next = node.getNext();
        while (next != null && (next.getOpcode() < 0 || next.getOpcode() == Opcodes.NOP)) {
            next = next.getNext();
        }
        return next;
    }
}
