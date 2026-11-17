package games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HellforgedMixinPluginTest {
    private static ClassNode nativeHelper() throws Exception {
        try (var jar = new JarFile(Path.of(System.getProperty("mismatchedthings.test.neoVitaeHellforgedJar")).toFile())) {
            var node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry("com/breakinblocks/neovitae/common/item/soul/HellforgedHelper.class")))
                    .accept(node, 0);
            return node;
        }
    }

    @Test
    void acceptsActualNativeReadAndUnrelatedEdits() throws Exception {
        var node = nativeHelper();
        assertTrue(HellforgedMixinPlugin.supports(node));
        node.methods.stream().filter(method -> method.name.equals("maxUpgradePoints")).findFirst().orElseThrow()
                .instructions.insert(new InsnNode(Opcodes.NOP));
        node.methods.stream().filter(method -> method.name.equals("isHellforged")).findFirst().orElseThrow()
                .instructions.insert(new InsnNode(Opcodes.NOP));
        assertTrue(HellforgedMixinPlugin.supports(node));
    }

    @Test
    void rejectsAnotherMarkerOrMissingNativeRead() throws Exception {
        var node = nativeHelper();
        var getter = node.methods.stream().filter(method -> method.name.equals("isHellforged")).findFirst().orElseThrow();
        for (var instruction : getter.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("HELLFORGED")) {
                field.name = "BLOOD_MENDING";
            }
        }
        assertFalse(HellforgedMixinPlugin.supports(node));
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(Opcodes.ICONST_0));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
        assertFalse(HellforgedMixinPlugin.supports(node));
    }
}
