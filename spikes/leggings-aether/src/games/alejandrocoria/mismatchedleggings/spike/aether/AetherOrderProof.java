package games.alejandrocoria.mismatchedleggings.spike.aether;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Applies the actual packaged Boots edits, not a hand-written substitute. */
final class AetherOrderProof {
    private AetherOrderProof() {}

    static void verify() throws Exception {
        var report = new StringBuilder();
        try (var nativeJar = new JarFile(System.getProperty("leggings.test.aetherNativeJar"));
             var loader = new URLClassLoader(new java.net.URL[]{Path.of(System.getProperty("leggings.test.aetherBootsJar")).toUri().toURL()},
                     AetherOrderProof.class.getClassLoader())) {
            for (String target : List.of("item/equipment/EquipmentUtil", "block/fluid/AlkahestFluid", "AetherIIEventListeners")) {
                var nativeName = "com/aetherteam/aetherii/" + target;
                var bytes = nativeJar.getInputStream(nativeJar.getJarEntry(nativeName + ".class")).readAllBytes();
                String shape = target.endsWith("EquipmentUtil") ? "AetherRecognitionShape" : "AetherAlkahestShape";
                var type = loader.loadClass("games.alejandrocoria.mismatchedboots.compatibility.aether." + shape);
                var apply = type.getDeclaredMethod("apply", ClassNode.class);
                apply.setAccessible(true);
                for (boolean legsFirst : List.of(false, true)) {
                    var node = new ClassNode();
                    new ClassReader(bytes).accept(node, 0);
                    if (legsFirst) { AetherTransforms.apply(node); }
                    require((boolean) apply.invoke(null, node), "Packaged Boots proof refused candidate order: " + target + "/" + legsFirst);
                    if (!legsFirst) { AetherTransforms.apply(node); }
                    var method = node.methods.stream().filter(m -> m.name.equals(target.endsWith("EquipmentUtil") ? "getArmorCount"
                            : target.endsWith("AlkahestFluid") ? "entityInside" : "onArmorDamaged")).findFirst().orElseThrow();
                    new Analyzer<>(new BasicInterpreter()).analyze(node.name, method);
                    report.append(target).append(legsFirst ? ": Leggings -> actual Boots" : ": actual Boots -> Leggings")
                            .append("; native query and verifier passed\n");
                }
            }
            var bytes = nativeJar.getInputStream(nativeJar.getJarEntry("com/aetherteam/aetherii/AetherIIEventListeners.class")).readAllBytes();
            var type = loader.loadClass("games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipShape");
            var producer = type.getDeclaredMethod("producer", ClassNode.class, boolean.class);
            producer.setAccessible(true);
            for (boolean legsFirst : List.of(false, true)) {
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                if (legsFirst) { AetherTransforms.presentation(node); }
                for (boolean attributes : List.of(false, true)) {
                    require((boolean) producer.invoke(null, node, attributes), "Actual Boots tooltip producer refused sibling order");
                }
                if (!legsFirst) { AetherTransforms.presentation(node); }
                for (var method : node.methods) {
                    if (method.name.equals("onAddAttributeTooltips") || method.name.equals("onAddTooltipsLowest")) {
                        new Analyzer<>(new BasicInterpreter()).analyze(node.name, method);
                    }
                }
                report.append("Native tooltip/tier producers: ").append(legsFirst ? "Leggings -> actual Boots" : "actual Boots -> Leggings")
                        .append("; original-call argument proofs and verifier passed\n");
            }
        }
        Files.writeString(AetherSpike.report("aether-transform-orders.txt"), report);
    }
}
