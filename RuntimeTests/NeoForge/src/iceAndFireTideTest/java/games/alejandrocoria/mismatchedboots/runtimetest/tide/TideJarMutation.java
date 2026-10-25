package games.alejandrocoria.mismatchedboots.runtimetest.tide;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Build-only native API mutation; the original optional-mod JAR is never modified. */
public final class TideJarMutation {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), target = Path.of(args[1]);
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Never mutate the original fixture artifact");
        }
        Files.createDirectories(target.getParent());
        boolean changed = false;
        try (ZipFile input = new ZipFile(source.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                byte[] bytes;
                try (var stream = input.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                if (entry.getName().equals(TideFixturePlugin.ARMOR + ".class")) {
                    ClassNode node = new ClassNode();
                    new ClassReader(bytes).accept(node, 0);
                    var field = node.fields.stream()
                                           .filter(f -> f.name.equals("type") && f.desc.equals("Lnet/minecraft/world/item/equipment/ArmorType;"))
                                           .findFirst()
                                           .orElseThrow();
                    field.name = "renamedType";
                    int references = 0;
                    for (var method : node.methods) {
                        for (var instruction : method.instructions) {
                            if (instruction instanceof FieldInsnNode access && access.owner.equals(node.name)
                                    && access.name.equals("type") && access.desc.equals(field.desc)) {
                                access.name = field.name;
                                references++;
                            }
                        }
                    }
                    if (references < 2) {
                        throw new AssertionError("Expected native constructor and tick field references");
                    }
                    ClassWriter writer = new ClassWriter(0);
                    node.accept(writer);
                    bytes = writer.toByteArray();
                    changed = true;
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(bytes);
                output.closeEntry();
            }
        }
        if (!changed) {
            throw new AssertionError("Missing native Tide class in fixture copy");
        }
    }
}
