package games.alejandrocoria.mismatchedthings.api.compatibility.justdirethings;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NeoForgeItemConsumptionProofTest {
    @Test
    void earlyProofLoadsAndRefusesUnknownBytecodeWithoutGameLoaderOrOptionalModClasses() throws Exception {
        // Only core bytecode and ASM are visible; Minecraft, NeoForge and JDT are deliberately absent.
        var urls = new URL[]{location(NeoForgeItemConsumptionProof.class), location(ClassReader.class),
                location(ClassNode.class), location(Analyzer.class)};
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var api = Class.forName(NeoForgeItemConsumptionProof.class.getName(), true, loader);
            var nodeType = Class.forName(ClassNode.class.getName(), true, loader);
            var unknown = nodeType.getConstructor().newInstance();
            assertFalse((boolean) api.getMethod("boundary", nodeType).invoke(null, unknown));
        }
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
