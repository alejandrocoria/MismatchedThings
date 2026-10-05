package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
    // Installed CE 2.2-beta.3, characterized without debug metadata; re-audit executable changes.
    static final String AUDITED_FINGERPRINT =
            "05b8d47b296aa4ad4de6e3aa9c1a477fc14789d5265617a5c518b1553d1bbdc9";
    static final String LIGHTNING_FINGERPRINT =
            "27d4490cc0ac9741b62dcbc4d2e39422cf12dbb8964c82decdf5cf41666f6169";

    private IceAndFireDamageShape() {
    }

    static boolean isCompatible(ClassNode handler, ClassNode scale, ClassNode steel, ClassNode troll) {
        return isCompatible(handler, scale, steel, troll, AUDITED_FINGERPRINT);
    }

    static boolean isCompatible(ClassNode handler, ClassNode scale, ClassNode steel, ClassNode troll,
                                String expectedFingerprint) {
        if (handler == null || !HANDLER.equals(handler.name)
                || !isPublicItemClass(scale, SCALE) || !isPublicItemClass(steel, STEEL)
                || !isPublicItemClass(troll, TROLL)) {
            return false;
        }
        MethodNode method = handler.methods.stream()
                .filter(candidate -> METHOD.equals(candidate.name) && DESCRIPTOR.equals(candidate.desc))
                .findFirst().orElse(null);
        return isPublicHandler(method)
                && feetChecks(method.instructions).size() == 3
                && expectedFingerprint.equals(fingerprint(method));
    }

    static boolean isLightningCompatible(ClassNode handler, ClassNode items) {
        return isLightningCompatible(handler, items, LIGHTNING_FINGERPRINT);
    }

    static boolean isLightningCompatible(ClassNode handler, ClassNode items, String expectedFingerprint) {
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
        return isPublicHandler(method) && lightningFeetChecks(method.instructions).size() == 1
                && expectedFingerprint.equals(fingerprint(method));
    }

    private static boolean isPublicHandler(MethodNode method) {
        return method != null && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0
                && method.tryCatchBlocks.isEmpty();
    }

    private static boolean isPublicItemClass(ClassNode node, String name) {
        return node != null && name.equals(node.name)
                && "net/minecraft/world/item/Item".equals(node.superName)
                && (node.access & Opcodes.ACC_PUBLIC) != 0
                && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    /** Select only native FEET class operations: Troll, then Scale/Steel; never other-slot checks. */
    public static List<AbstractInsnNode> feetChecks(InsnList instructions) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode node : instructions) {
            if (node.getOpcode() >= 0) {
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
                    && entity.var == 1
                    && code.get(index - 3) instanceof FieldInsnNode slot
                    && slot.getOpcode() == Opcodes.GETSTATIC
                    && "net/minecraft/world/entity/EquipmentSlot".equals(slot.owner)
                    && "FEET".equals(slot.name) && ("L" + slot.owner + ";").equals(slot.desc)
                    && isCall(code.get(index - 2), "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                        "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
                    && isCall(code.get(index - 1), "net/minecraft/world/item/ItemStack", "getItem",
                        "()Lnet/minecraft/world/item/Item;")) {
                result.add(check);
            }
        }
        if (result.size() != 3 || !TROLL.equals(((TypeInsnNode) result.get(0)).desc)
                || !SCALE.equals(((TypeInsnNode) result.get(1)).desc)
                || !STEEL.equals(((TypeInsnNode) result.get(2)).desc)) {
            return List.of();
        }
        return List.copyOf(result);
    }

    /** Select the native exact-item predicate, not the equipment read or the three other set checks. */
    public static List<AbstractInsnNode> lightningFeetChecks(InsnList instructions) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode node : instructions) {
            if (node.getOpcode() >= 0) {
                code.add(node);
            }
        }
        List<AbstractInsnNode> result = new ArrayList<>();
        for (int index = 6; index < code.size(); index++) {
            if (isCall(code.get(index), "net/minecraft/world/item/ItemStack", "is", "(Ljava/lang/Object;)Z")
                    && code.get(index - 6) instanceof VarInsnNode entity && entity.getOpcode() == Opcodes.ALOAD
                    && entity.var == 1
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
                    && "net/minecraft/world/item/Item".equals(cast.desc)) {
                result.add(code.get(index));
            }
        }
        return result.size() == 1 ? List.copyOf(result) : List.of();
    }

    private static boolean isCall(AbstractInsnNode node, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }

    static String fingerprint(MethodNode method) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "AuditedHandler", null, "java/lang/Object", null);
        var visitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                method.name, method.desc, null, null);
        visitor.visitCode();
        // Fresh labels avoid retaining ASM writer offsets. Frame/line/local metadata is not executable.
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof LabelNode label) {
                labels.put(label, new LabelNode());
            }
        }
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() >= 0 || node instanceof LabelNode) {
                node.clone(labels).accept(visitor);
            }
        }
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
        writer.visitEnd();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
