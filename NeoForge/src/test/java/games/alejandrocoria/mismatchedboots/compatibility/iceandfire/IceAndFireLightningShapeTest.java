package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IceAndFireLightningShapeTest {
    @Test
    void installedArtifactCharacterizesExactFeetPredicateAndRejectsChangedSetItem() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Real-artifact characterization is opt-in");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            ClassNode handler = read(jar, IceAndFireDamageShape.HANDLER);
            ClassNode items = read(jar, IceAndFireDamageShape.ITEMS);
            MethodNode method = handler.methods.stream()
                    .filter(node -> IceAndFireDamageShape.LIGHTNING_METHOD.equals(node.name))
                    .findFirst().orElseThrow();
            String fingerprint = IceAndFireDamageShape.fingerprint(method);
            System.out.println("Ice and Fire audited Lightning handler fingerprint: " + fingerprint);
            assertEquals(IceAndFireDamageShape.LIGHTNING_FINGERPRINT, fingerprint);
            assertEquals(1, IceAndFireDamageShape.lightningFeetChecks(method.instructions).size());
            assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, items));
            assertTrue(IceAndFireLightningFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
            assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
            // A change to another set piece must also disable the adapter, not just changes to feet.
            for (var node : method.instructions) {
                if (node instanceof FieldInsnNode field && "DRAGONSTEEL_LIGHTNING_HELMET".equals(field.name)) {
                    field.name = "DRAGONSTEEL_FIRE_HELMET";
                    break;
                }
            }
            assertEquals(1, IceAndFireDamageShape.lightningFeetChecks(method.instructions).size());
            assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
            assertFalse(IceAndFireLightningFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
        }
    }

    @Test
    void selectorRejectsChangedSlotOrItemAndDoesNotSelectOtherSetPieces() {
        MethodNode method = fixture();
        assertEquals(1, IceAndFireDamageShape.lightningFeetChecks(method.instructions).size());
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field && "DRAGONSTEEL_LIGHTNING_BOOTS".equals(field.name)) {
                field.name = "DRAGONSTEEL_FIRE_BOOTS";
            }
        }
        assertTrue(IceAndFireDamageShape.lightningFeetChecks(method.instructions).isEmpty());
        method = fixture();
        for (var node : method.instructions) {
            if (node instanceof FieldInsnNode field && "FEET".equals(field.name)) {
                field.name = "HEAD";
            }
        }
        assertTrue(IceAndFireDamageShape.lightningFeetChecks(method.instructions).isEmpty());
    }

    @Test
    void lightningGuardIsIndependentAndRejectsMissingApiAndExecutableChanges() {
        MethodNode method = fixture();
        ClassNode handler = new ClassNode();
        handler.name = IceAndFireDamageShape.HANDLER;
        handler.methods.add(method); // No projectile/dragon handler is needed to guard this block.
        ClassNode items = new ClassNode();
        items.name = IceAndFireDamageShape.ITEMS;
        items.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "DRAGONSTEEL_LIGHTNING_BOOTS",
                "Lnet/neoforged/neoforge/registries/DeferredItem;", null, null));
        String fingerprint = IceAndFireDamageShape.fingerprint(method);
        assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(null, items, fingerprint));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, null, fingerprint));
        items.fields.getFirst().desc = "Ljava/lang/Object;";
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
        items.fields.getFirst().desc = "Lnet/neoforged/neoforge/registries/DeferredItem;";
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        LabelNode debug = new LabelNode();
        method.instructions.insert(debug);
        method.instructions.insert(debug, new LineNumberNode(789, debug));
        assertTrue(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
        method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(Opcodes.NOP));
        assertFalse(IceAndFireDamageShape.isLightningCompatible(handler, items, fingerprint));
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static MethodNode fixture() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                IceAndFireDamageShape.LIGHTNING_METHOD, IceAndFireDamageShape.DESCRIPTOR, null, null);
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            String piece = switch (slot) {
                case "HEAD" -> "HELMET";
                case "CHEST" -> "CHESTPLATE";
                case "LEGS" -> "LEGGINGS";
                default -> "BOOTS";
            };
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot",
                    slot, "Lnet/minecraft/world/entity/EquipmentSlot;"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity",
                    "getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
            method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, IceAndFireDamageShape.ITEMS,
                    "DRAGONSTEEL_LIGHTNING_" + piece, "Lnet/neoforged/neoforge/registries/DeferredItem;"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "net/neoforged/neoforge/registries/DeferredItem", "get", "()Ljava/lang/Object;", false));
            method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/item/Item"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack",
                    "is", "(Ljava/lang/Object;)Z", false));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }
}
