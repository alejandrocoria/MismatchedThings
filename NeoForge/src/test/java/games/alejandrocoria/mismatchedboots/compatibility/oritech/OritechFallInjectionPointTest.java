package games.alejandrocoria.mismatchedboots.compatibility.oritech;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OritechFallInjectionPointTest {
    @Test
    void installedArtifactAndUnrelatedSoundEditsRemainCompatible() throws Exception {
        String path = System.getProperty("mismatchedboots.test.oritechGuardJar");
        assumeTrue(path != null, "Real-artifact characterization is opt-in");
        try (var jar = new JarFile(path)) {
            var target = new ClassNode();
            try (var stream = jar.getInputStream(jar.getJarEntry(OritechFallInjectionPoint.TARGET.replace('.', '/') + ".class"))) {
                new ClassReader(stream).accept(target, 0);
            }

            var method = target.methods.stream().filter(node -> node.name.equals(OritechFallInjectionPoint.METHOD))
                                       .findFirst().orElseThrow();
            var selected = OritechFallInjectionPoint.select(method.desc, method.instructions);
            assertNotNull(selected);
            method.instructions.insert(new InsnNode(Opcodes.NOP));
            method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Observer", "tick", "()V", false));
            for (var node : method.instructions) {
                if (node instanceof LdcInsnNode constant && Float.valueOf(.2F).equals(constant.cst)) {
                    constant.cst = .25F;
                }
            }
            assertSame(selected, OritechFallInjectionPoint.select(method.desc, method.instructions));
        }
    }

    @Test
    void wrongSlotItemReceiverOrBooleanConsumerCannotGrantProtection() {
        for (String fault : new String[]{"slot", "item", "receiver", "consumer", "damage", "signature", "overwrite"}) {
            MethodNode method = fixture();
            switch (fault) {
                case "slot" -> ((FieldInsnNode) method.instructions.get(5)).name = "HEAD";
                case "item" -> ((FieldInsnNode) method.instructions.get(7)).name = "EXO_HELMET";
                case "receiver" -> ((VarInsnNode) method.instructions.get(4)).var = 1;
                case "consumer" -> ((JumpInsnNode) method.instructions.get(9)).setOpcode(Opcodes.IFNE);
                case "damage" -> ((FieldInsnNode) method.instructions.get(1)).name = "GENERIC";
                case "signature" -> method.desc = "()Z";
                case "overwrite" -> method.instructions.insert(new VarInsnNode(Opcodes.ASTORE, 2));
                default -> throw new AssertionError(fault);
            }
            assertNull(OritechFallInjectionPoint.select(method.desc, method.instructions), fault);
        }
    }

    @Test
    void missingAndAmbiguousChecksFailClosedWhileMetadataIsIgnored() {
        MethodNode method = fixture();
        assertNotNull(OritechFallInjectionPoint.select(method.desc, method.instructions));
        method.instructions.insert(new LabelNode());
        method.instructions.insert(new InsnNode(Opcodes.NOP));
        assertNotNull(OritechFallInjectionPoint.select(method.desc, method.instructions));
        method.instructions.add(fixture().instructions);
        assertNull(OritechFallInjectionPoint.select(method.desc, method.instructions));
        method = fixture();
        method.instructions.remove(method.instructions.get(8));
        assertNull(OritechFallInjectionPoint.select(method.desc, method.instructions));
    }

    private static MethodNode fixture() {
        var method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, OritechFallInjectionPoint.METHOD,
                                    OritechFallInjectionPoint.DESCRIPTOR, null, null);
        LabelNode reject = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/damagesource/DamageTypes",
                                                  "FALL", "Lnet/minecraft/resources/ResourceKey;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/damagesource/DamageSource",
                                                   "is", "(Lnet/minecraft/resources/ResourceKey;)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, reject));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot",
                                                  "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                                   "net/minecraft/world/entity/LivingEntity",
                                                   "getItemBySlot",
                                                   "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
                                                   false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "rearth/oritech/init/ToolsContent",
                                                  "EXO_BOOTS", "Lnet/neoforged/neoforge/registries/DeferredItem;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack",
                                                   "is", "(Lnet/minecraft/core/Holder;)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, reject));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(reject);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }
}
