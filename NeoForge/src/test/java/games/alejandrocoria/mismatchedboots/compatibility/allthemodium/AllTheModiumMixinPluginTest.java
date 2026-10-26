package games.alejandrocoria.mismatchedboots.compatibility.allthemodium;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllTheModiumMixinPluginTest {
    @Test
    void pinnedHelperAndCompatibleObserversKeepTheReturnBoundary() throws Exception {
        ClassNode owner = nativeClass();
        assertTrue(AllTheModiumMixinPlugin.supports(owner));

        var helper = owner.methods.stream().filter(method -> method.name.equals(AllTheModiumMixinPlugin.METHOD))
                                  .findFirst().orElseThrow();
        helper.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "fixture/Observer", "visit", "()V", false));
        helper.instructions.insert(new InsnNode(Opcodes.NOP));
        assertTrue(AllTheModiumMixinPlugin.supports(owner));
    }

    @Test
    void changedHelperContractIsRejectedWithoutRequiringAWholeHandlerFingerprint() throws Exception {
        for (String fault : new String[]{"slotType", "instance", "removed", "noReturn"}) {
            ClassNode owner = nativeClass();
            var helper = owner.methods.stream().filter(method -> method.name.equals(AllTheModiumMixinPlugin.METHOD))
                                      .findFirst().orElseThrow();
            switch (fault) {
                case "slotType" -> helper.desc = helper.desc.replace("EquipmentSlot", "EquipmentSlotGroup");
                case "instance" -> helper.access &= ~Opcodes.ACC_STATIC;
                case "removed" -> owner.methods.remove(helper);
                case "noReturn" -> {
                    helper.instructions.clear();
                    helper.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    helper.instructions.add(new InsnNode(Opcodes.ATHROW));
                }
                default -> throw new AssertionError(fault);
            }
            assertFalse(AllTheModiumMixinPlugin.supports(owner), fault);
        }
    }

    private static ClassNode nativeClass() throws Exception {
        String path = System.getProperty("mismatchedboots.test.allthemodiumGuardJar");
        assertNotNull(path, "The Gradle test fixture must resolve its own pinned native JAR");
        try (var jar = new JarFile(path);
             var input = jar.getInputStream(jar.getJarEntry(AllTheModiumMixinPlugin.TARGET + ".class"))) {
            var owner = new ClassNode();
            new ClassReader(input).accept(owner, 0);
            return owner;
        }
    }
}
