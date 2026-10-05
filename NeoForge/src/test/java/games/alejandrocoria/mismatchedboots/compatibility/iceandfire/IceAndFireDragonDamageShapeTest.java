package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
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

class IceAndFireDragonDamageShapeTest {
    @Test
    void installedArtifactCharacterizesTheActualNativeHandler() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Real-artifact characterization is opt-in; ordinary builds need no indev JAR");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            ClassNode handler = read(jar, IceAndFireDragonDamageShape.HANDLER);
            MethodNode method = handler.methods.stream()
                    .filter(node -> IceAndFireDragonDamageShape.METHOD.equals(node.name)).findFirst().orElseThrow();
            assertEquals(2, IceAndFireDragonDamageShape.feetChecks(method.instructions).size());
            System.out.println("Ice and Fire audited handler fingerprint: "
                    + IceAndFireDragonDamageShape.fingerprint(method));
            ClassNode scale = read(jar, IceAndFireDragonDamageShape.SCALE);
            ClassNode steel = read(jar, IceAndFireDragonDamageShape.STEEL);
            String fingerprint = IceAndFireDragonDamageShape.fingerprint(method);
            assertEquals(IceAndFireDragonDamageShape.AUDITED_FINGERPRINT, fingerprint);
            assertTrue(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel));
            assertTrue(IceAndFireDragonFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
            assertTrue(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
            // Detect a real semantic change even when both injection points are still present.
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof LdcInsnNode constant && Float.valueOf(0.1F).equals(constant.cst)) {
                    constant.cst = 0.05F;
                    break;
                }
            }
            assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
            assertFalse(IceAndFireDragonFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
        }
    }

    @Test
    void selectorExcludesOtherSlotsAndTrollAndRejectsChangedFeetReads() {
        MethodNode method = fixture();
        var selected = IceAndFireDragonDamageShape.feetChecks(method.instructions);
        assertEquals(2, selected.size());
        assertEquals(IceAndFireDragonDamageShape.SCALE, ((TypeInsnNode) selected.getFirst()).desc);
        assertEquals(IceAndFireDragonDamageShape.STEEL, ((TypeInsnNode) selected.getLast()).desc);
        ((FieldInsnNode) selected.getLast().getPrevious().getPrevious().getPrevious()).name = "HEAD";
        assertTrue(IceAndFireDragonDamageShape.feetChecks(method.instructions).isEmpty());
    }

    @Test
    void semanticAndControlFlowChangesDisableTheAdapterButDebugMetadataDoesNot() {
        MethodNode method = fixture();
        ClassNode handler = handler(method);
        ClassNode scale = armor(IceAndFireDragonDamageShape.SCALE);
        ClassNode steel = armor(IceAndFireDragonDamageShape.STEEL);
        String fingerprint = IceAndFireDragonDamageShape.fingerprint(method);
        assertTrue(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));

        LabelNode debug = new LabelNode();
        method.instructions.insert(debug);
        method.instructions.insert(debug, new LineNumberNode(999, debug));
        assertTrue(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));

        method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(Opcodes.NOP));
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
        method.instructions.remove(method.instructions.getLast().getPrevious());
        assertTrue(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));

        JumpInsnNode branch = new JumpInsnNode(Opcodes.GOTO, debug);
        method.instructions.insertBefore(method.instructions.getLast(), branch);
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
    }

    @Test
    void missingOrIncompatibleOptionalTypesAndEventSignaturesAreRejected() {
        MethodNode method = fixture();
        ClassNode handler = handler(method);
        ClassNode scale = armor(IceAndFireDragonDamageShape.SCALE);
        ClassNode steel = armor(IceAndFireDragonDamageShape.STEEL);
        String fingerprint = IceAndFireDragonDamageShape.fingerprint(method);
        assertFalse(IceAndFireDragonDamageShape.isCompatible(null, scale, steel, fingerprint));
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, null, steel, fingerprint));
        steel.superName = "different/ArmorBase";
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
        steel.superName = "net/minecraft/world/item/Item";
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.desc = "(Ljava/lang/Object;)V";
        assertFalse(IceAndFireDragonDamageShape.isCompatible(handler, scale, steel, fingerprint));
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static ClassNode armor(String name) {
        ClassNode node = new ClassNode();
        node.name = name;
        node.superName = "net/minecraft/world/item/Item";
        node.access = Opcodes.ACC_PUBLIC;
        return node;
    }

    private static ClassNode handler(MethodNode method) {
        ClassNode node = new ClassNode();
        node.name = IceAndFireDragonDamageShape.HANDLER;
        node.methods.add(method);
        return node;
    }

    private static MethodNode fixture() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                IceAndFireDragonDamageShape.METHOD, IceAndFireDragonDamageShape.DESCRIPTOR, null, null);
        addCheck(method, "FEET", "com/iafenvoy/iceandfire/item/armor/TrollArmorItem");
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            addCheck(method, slot, IceAndFireDragonDamageShape.SCALE);
            addCheck(method, slot, IceAndFireDragonDamageShape.STEEL);
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static void addCheck(MethodNode method, String slot, String type) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "net/minecraft/world/entity/EquipmentSlot", slot, "Lnet/minecraft/world/entity/EquipmentSlot;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;", false));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, type));
        method.instructions.add(new InsnNode(Opcodes.POP));
    }
}
