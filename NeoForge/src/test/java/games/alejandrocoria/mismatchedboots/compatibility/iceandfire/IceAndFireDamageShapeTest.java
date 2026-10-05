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

class IceAndFireDamageShapeTest {
    @Test
    void installedArtifactCharacterizesTheActualNativeHandler() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Real-artifact characterization is opt-in; ordinary builds need no indev JAR");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            ClassNode handler = read(jar, IceAndFireDamageShape.HANDLER);
            MethodNode method = handler.methods.stream()
                    .filter(node -> IceAndFireDamageShape.METHOD.equals(node.name)).findFirst().orElseThrow();
            assertEquals(3, IceAndFireDamageShape.feetChecks(method.instructions).size());
            System.out.println("Ice and Fire audited handler fingerprint: "
                    + IceAndFireDamageShape.fingerprint(method));
            ClassNode scale = read(jar, IceAndFireDamageShape.SCALE);
            ClassNode steel = read(jar, IceAndFireDamageShape.STEEL);
            ClassNode troll = read(jar, IceAndFireDamageShape.TROLL);
            String fingerprint = IceAndFireDamageShape.fingerprint(method);
            assertEquals(IceAndFireDamageShape.AUDITED_FINGERPRINT, fingerprint);
            assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll));
            assertTrue(IceAndFireDamageFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
            assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
            // Detect a real semantic change even when all three feet operations are still present.
            for (AbstractInsnNode node : method.instructions) {
                if (node instanceof LdcInsnNode constant && Float.valueOf(0.1F).equals(constant.cst)) {
                    constant.cst = 0.05F;
                    break;
                }
            }
            assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
            assertFalse(IceAndFireDamageFeetInjectionPoint.matchesAuditedInstructions(method.desc, method.instructions));
        }
    }

    @Test
    void selectorIncludesOnlyTrollAndDragonFeetAndRejectsChangedFeetReads() {
        MethodNode method = fixture();
        var selected = IceAndFireDamageShape.feetChecks(method.instructions);
        assertEquals(3, selected.size());
        assertEquals(IceAndFireDamageShape.TROLL, ((TypeInsnNode) selected.getFirst()).desc);
        assertEquals(IceAndFireDamageShape.SCALE, ((TypeInsnNode) selected.get(1)).desc);
        assertEquals(IceAndFireDamageShape.STEEL, ((TypeInsnNode) selected.getLast()).desc);
        ((FieldInsnNode) selected.getLast().getPrevious().getPrevious().getPrevious()).name = "HEAD";
        assertTrue(IceAndFireDamageShape.feetChecks(method.instructions).isEmpty());
        method = fixture();
        var trollCheck = IceAndFireDamageShape.feetChecks(method.instructions).getFirst();
        ((FieldInsnNode) trollCheck.getPrevious().getPrevious().getPrevious()).name = "HEAD";
        assertTrue(IceAndFireDamageShape.feetChecks(method.instructions).isEmpty());
    }

    @Test
    void semanticAndControlFlowChangesDisableTheAdapterButDebugMetadataDoesNot() {
        MethodNode method = fixture();
        ClassNode handler = handler(method);
        ClassNode scale = armor(IceAndFireDamageShape.SCALE);
        ClassNode steel = armor(IceAndFireDamageShape.STEEL);
        ClassNode troll = armor(IceAndFireDamageShape.TROLL);
        String fingerprint = IceAndFireDamageShape.fingerprint(method);
        assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));

        LabelNode debug = new LabelNode();
        method.instructions.insert(debug);
        method.instructions.insert(debug, new LineNumberNode(999, debug));
        assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));

        method.instructions.insertBefore(method.instructions.getLast(), new InsnNode(Opcodes.NOP));
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
        method.instructions.remove(method.instructions.getLast().getPrevious());
        assertTrue(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));

        JumpInsnNode branch = new JumpInsnNode(Opcodes.GOTO, debug);
        method.instructions.insertBefore(method.instructions.getLast(), branch);
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
    }

    @Test
    void missingOrIncompatibleOptionalTypesAndEventSignaturesAreRejected() {
        MethodNode method = fixture();
        ClassNode handler = handler(method);
        ClassNode scale = armor(IceAndFireDamageShape.SCALE);
        ClassNode steel = armor(IceAndFireDamageShape.STEEL);
        ClassNode troll = armor(IceAndFireDamageShape.TROLL);
        String fingerprint = IceAndFireDamageShape.fingerprint(method);
        assertFalse(IceAndFireDamageShape.isCompatible(null, scale, steel, troll, fingerprint));
        assertFalse(IceAndFireDamageShape.isCompatible(handler, null, steel, troll, fingerprint));
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, null, fingerprint));
        troll.superName = "different/TrollBase";
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
        troll.superName = "net/minecraft/world/item/Item";
        steel.superName = "different/ArmorBase";
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
        steel.superName = "net/minecraft/world/item/Item";
        method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.desc = "(Ljava/lang/Object;)V";
        assertFalse(IceAndFireDamageShape.isCompatible(handler, scale, steel, troll, fingerprint));
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
        node.name = IceAndFireDamageShape.HANDLER;
        node.methods.add(method);
        return node;
    }

    private static MethodNode fixture() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                IceAndFireDamageShape.METHOD, IceAndFireDamageShape.DESCRIPTOR, null, null);
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            addCheck(method, slot, IceAndFireDamageShape.TROLL);
        }
        for (String slot : new String[]{"HEAD", "CHEST", "LEGS", "FEET"}) {
            addCheck(method, slot, IceAndFireDamageShape.SCALE);
            addCheck(method, slot, IceAndFireDamageShape.STEEL);
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
