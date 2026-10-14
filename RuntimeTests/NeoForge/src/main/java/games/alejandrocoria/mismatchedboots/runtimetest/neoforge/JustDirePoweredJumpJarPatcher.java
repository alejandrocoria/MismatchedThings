package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Build-only research copy: replace one read-only resolver, never the normal JAR or source code. */
public final class JustDirePoweredJumpJarPatcher {
    private static final String TARGET = "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireJumpCompatibility.class";

    private JustDirePoweredJumpJarPatcher() {}

    public static void main(String[] args) throws Exception {
        var source = Path.of(args[0]).toAbsolutePath().normalize(); var output = Path.of(args[1]).toAbsolutePath().normalize();
        if (source.equals(output)) { throw new IllegalArgumentException("Never overwrite the production JAR"); }
        Files.createDirectories(output.getParent()); boolean replaced = false;
        try (var input = new ZipInputStream(Files.newInputStream(source)); var result = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] bytes = input.readAllBytes();
                if (entry.getName().equals(TARGET)) { bytes = patched(bytes); replaced = true; }
                result.putNextEntry(new ZipEntry(entry.getName())); result.write(bytes); result.closeEntry();
            }
        }
        if (!replaced) { throw new IllegalStateException("Production Jump resolver class not found"); }
        System.out.println("Research copy only: replaced JustDireJumpCompatibility.value; production archive unchanged");
    }

    private static byte[] patched(byte[] bytes) {
        var node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        var method = node.methods.stream().filter(candidate -> candidate.name.equals("value")
                && candidate.desc.equals("(Lnet/minecraft/world/item/ItemStack;)I")).findFirst().orElseThrow();
        if ((method.access & Opcodes.ACC_STATIC) == 0 || method.instructions.size() == 0) { throw new IllegalStateException("Unexpected query shape"); }
        method.instructions.clear(); method.tryCatchBlocks.clear();
        if (method.localVariables != null) { method.localVariables.clear(); }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/JustDirePoweredJumpSpike", "value", method.desc, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
