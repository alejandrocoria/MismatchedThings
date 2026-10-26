package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.jar.JarFile;

/** Native-shaped consumers; fingerprints characterize the pinned artifact, never production activation. */
final class IceAndFireGuardFixture {
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre";
    static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";

    static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    static ClassNode handler(MethodNode method) {
        ClassNode node = new ClassNode();
        node.name = IceAndFireDamageShape.HANDLER;
        node.methods.add(method);
        return node;
    }

    static ClassNode armor(String name) {
        ClassNode node = new ClassNode();
        node.name = name;
        node.superName = "net/minecraft/world/item/Item";
        node.access = Opcodes.ACC_PUBLIC;
        return node;
    }

    static MethodNode start(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                           name, IceAndFireDamageShape.DESCRIPTOR, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT, "getEntity",
                                                   "()L" + ENTITY + ";", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        return method;
    }

    static void equipment(MethodNode method, String slot) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, SLOT, slot, "L" + SLOT + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "getItemBySlot",
                                                   "(L" + SLOT + ";)L" + STACK + ";", false));
    }

    static void classCheck(MethodNode method, String slot, String type) {
        equipment(method, slot);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STACK, "getItem",
                                                   "()Lnet/minecraft/world/item/Item;", false));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
    }

    static void contribution(MethodNode method) {
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 4));
        method.instructions.add(new LdcInsnNode(0.1F));
        method.instructions.add(new InsnNode(Opcodes.FSUB));
        method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 4));
    }

    static MethodNode damage() {
        MethodNode method = start(IceAndFireDamageShape.METHOD);
        method.instructions.add(new InsnNode(Opcodes.FCONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 4));
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            LabelNode skip = new LabelNode();
            classCheck(method, slot, IceAndFireDamageShape.TROLL);
            method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            contribution(method);
            method.instructions.add(skip);
        }
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            LabelNode yes = new LabelNode();
            LabelNode skip = new LabelNode();
            classCheck(method, slot, IceAndFireDamageShape.SCALE);
            method.instructions.add(new JumpInsnNode(Opcodes.IFNE, yes));
            classCheck(method, slot, IceAndFireDamageShape.STEEL);
            method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            method.instructions.add(yes);
            contribution(method);
            method.instructions.add(skip);
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    static MethodNode lightning() {
        MethodNode method = start(IceAndFireDamageShape.LIGHTNING_METHOD);
        LabelNode skip = new LabelNode();
        String[] slots = {"HEAD", "CHEST", "LEGS", "FEET"};
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (int index = 0; index < slots.length; index++) {
            equipment(method, slots[index]);
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, IceAndFireDamageShape.ITEMS,
                                                      "DRAGONSTEEL_LIGHTNING_" + pieces[index],
                                                      "Lnet/neoforged/neoforge/registries/DeferredItem;"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                                       "net/neoforged/neoforge/registries/DeferredItem", "get", "()Ljava/lang/Object;", false));
            method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/item/Item"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, STACK, "is",
                                                       "(Ljava/lang/Object;)Z", false));
            method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT, "setNewDamage", "(F)V", false));
        method.instructions.add(skip);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
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
