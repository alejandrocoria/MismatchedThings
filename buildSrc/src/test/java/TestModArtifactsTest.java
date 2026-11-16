import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModArtifactsTest {
    @TempDir
    Path directory;

    @Test
    void directGuardsStayDirectWhileRuntimeRootsExpandTransitivelyAndDeduplicateAliases() {
        var root = entry("root.jar", List.of("library", "shared"), List.of("client"));
        var library = entry("library.jar", List.of("shared"), List.of());
        var shared = entry("shared.jar", List.of(), List.of());
        var client = entry("client.jar", List.of(), List.of());
        var old = entry("root-old.jar", List.of(), List.of());
        var catalog = new TestModArtifacts(Map.of(
                "Fabric/root", root, "Fabric/root.jar", root, "Fabric/root-old", old,
                "Fabric/library", library, "Fabric/shared", shared, "Fabric/client", client));

        assertEquals(List.of(root.target()), catalog.paths("Fabric", List.of("root", "root.jar")));
        assertEquals(List.of(root.target(), library.target(), shared.target()),
                     catalog.runtimePaths("Fabric", "server", List.of("root.jar", "library", "root")));
        assertEquals(List.of(root.target(), library.target(), shared.target(), client.target()),
                     catalog.runtimePaths("Fabric", "client", List.of("root")));
        assertEquals(List.of(old.target(), root.target(), library.target(), shared.target()),
                     catalog.runtimePaths("Fabric", "server", List.of("root-old", "root")));
        assertFalse(Files.exists(root.target().toPath()), "Metadata lookup must not stage or resolve a pin");
    }

    @Test
    void clientEdgesDoNotBanExplicitServerExtrasAndEmptySelectionsRemainEmpty() {
        var root = entry("root.jar", List.of(), List.of("client"));
        var client = entry("client.jar", List.of(), List.of());
        var catalog = new TestModArtifacts(Map.of("NeoForge/root", root, "NeoForge/client", client));

        assertEquals(List.of(root.target()), catalog.runtimePaths("NeoForge", "server", List.of("root")));
        assertEquals(List.of(root.target(), client.target()), catalog.runtimePaths("NeoForge", "server", List.of("root", "client")));
        assertTrue(catalog.paths("NeoForge", List.of()).isEmpty());
        assertTrue(catalog.runtimePaths("NeoForge", "client", List.of()).isEmpty());
        assertThrows(GradleException.class, () -> catalog.runtimePaths("NeoForge", "guard", List.of("root")));
    }

    @Test
    void unknownAndCrossLoaderDependenciesFailDuringMetadataRegistration() {
        var root = entry("root.jar", List.of("library"), List.of());
        var library = entry("library.jar", List.of(), List.of());
        var missing = assertThrows(GradleException.class, () -> new TestModArtifacts(Map.of("Fabric/root", root)));
        assertTrue(missing.getMessage().contains("Fabric/library"));
        assertThrows(GradleException.class,
                     () -> new TestModArtifacts(Map.of("Fabric/root", root, "NeoForge/library", library)));
        var catalog = new TestModArtifacts(Map.of("NeoForge/library", library));
        assertThrows(GradleException.class, () -> catalog.paths("Fabric", List.of("library")));
    }

    @Test
    void aliasCyclesIncludingClientOnlyEdgesAreRejectedBeforeAnyResolution() {
        var root = entry("root.jar", List.of("library.jar"), List.of());
        var library = entry("library.jar", List.of(), List.of("root.jar"));
        var failure = assertThrows(GradleException.class, () -> new TestModArtifacts(Map.of(
                "NeoForge/root", root, "NeoForge/root.jar", root,
                "NeoForge/library", library, "NeoForge/library.jar", library)));
        assertTrue(failure.getMessage().contains("dependency cycle"));
    }

    @Test
    void dependencyFieldsRequireArraysOfNonblankLoaderLocalSelectors() {
        File target = directory.resolve("root.jar").toFile();
        for (Object invalid : List.of("library", Map.of("library", true), List.of(42), List.of(" "), List.of("Fabric/library"))) {
            assertThrows(GradleException.class, () -> TestModArtifacts.entry(target, null, "unused", invalid, List.of()));
            assertThrows(GradleException.class, () -> TestModArtifacts.entry(target, null, "unused", List.of(), invalid));
        }
        assertThrows(GradleException.class, () -> TestModArtifacts.entry(target, null, "unused", null, List.of()));
    }

    @Test
    void suppliedPinVerificationNeverRepairsAnExplicitFileAndCachedResolutionStaysDirect() throws Exception {
        byte[] expected = {10, 20, 30};
        Path target = directory.resolve("root.jar");
        Files.write(target, expected);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
        var pin = TestModArtifacts.entry(target.toFile(), null, hash);
        var catalog = new TestModArtifacts(Map.of("Fabric/root", pin));

        assertEquals(List.of(target.toFile()), catalog.resolveAll("Fabric", List.of("root", "root")));
        assertEquals(List.of(target.toFile()), catalog.resolveRuntime("Fabric", "client", List.of("root")));
        Path supplied = directory.resolve("supplied.jar");
        Files.write(supplied, expected);
        assertEquals(supplied.toFile(), catalog.verify("Fabric", "root", supplied.toFile()));
        byte[] damaged = {1, 2, 3};
        Files.write(supplied, damaged);
        assertThrows(GradleException.class, () -> catalog.verify("Fabric", "root", supplied.toFile()));
        assertArrayEquals(damaged, Files.readAllBytes(supplied));
        assertArrayEquals(expected, Files.readAllBytes(target));
        assertThrows(GradleException.class, () -> catalog.verify("Fabric", "root", directory.resolve("missing.jar").toFile()));
    }

    private TestModArtifacts.Artifact entry(String filename, List<String> requires, List<String> clientRequires) {
        // Metadata-only tests have no resolvable configuration: accidental resolution cannot succeed.
        return TestModArtifacts.entry(directory.resolve(filename).toFile(), null, "unused", requires, clientRequires);
    }
}
