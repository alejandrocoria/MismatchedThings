import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiny Java-only builds exercise Gradle/JVM wiring, not a second Minecraft runtime matrix. */
class TestModArtifactBindingsTest {
    @TempDir
    Path directory;

    @Test
    void defaultsAreLazyAndExplicitOverridesTrackTheirOwnBytesWithoutResolvingTheDefault() throws Exception {
        Path fixture = fixture();
        run(fixture, false, "help");
        Path staged = fixture.resolve("build/staged/native.jar");
        assertFalse(Files.exists(staged), "Configuration must not resolve the pin");

        assertEquals(TaskOutcome.SUCCESS, run(fixture, false, "test").task(":test").getOutcome());
        assertEquals("native-default", Files.readString(fixture.resolve("observed.txt")));
        assertEquals(TaskOutcome.UP_TO_DATE, run(fixture, false, "test").task(":test").getOutcome());

        // A fallback resolution would now fail. Only the explicit alternate file is valid.
        Files.delete(staged);
        Files.delete(fixture.resolve("native.jar"));
        Path alternate = fixture.resolve("alternate.jar");
        Files.writeString(alternate, "override-one");
        assertEquals(TaskOutcome.SUCCESS, run(fixture, false, "test", "-PguardOverride=alternate.jar").task(":test").getOutcome());
        assertEquals("override-one", Files.readString(fixture.resolve("observed.txt")));
        assertEquals(TaskOutcome.UP_TO_DATE, run(fixture, false, "test", "-PguardOverride=alternate.jar").task(":test").getOutcome());

        Files.writeString(alternate, "override-two");
        assertEquals(TaskOutcome.SUCCESS, run(fixture, false, "test", "-PguardOverride=alternate.jar").task(":test").getOutcome());
        assertEquals("override-two", Files.readString(fixture.resolve("observed.txt")));
        assertFalse(Files.exists(staged));
    }

    @Test
    void generatedOverrideKeepsItsProducerAndMissingExplicitFilesNeverFallBack() throws Exception {
        Path fixture = fixture();
        Files.delete(fixture.resolve("native.jar"));
        BuildResult result = run(fixture, false, "test", "-PgeneratedOverride");
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateGuard").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":test").getOutcome());
        assertEquals("generated-override", Files.readString(fixture.resolve("observed.txt")));
        assertFalse(Files.exists(fixture.resolve("build/staged/native.jar")));

        BuildResult missing = run(fixture, true, "test", "-PguardOverride=missing.jar");
        assertTrue(missing.getOutput().contains("missing.jar"));
        assertTrue(missing.getOutput().contains("doesn't exist"));
        assertFalse(Files.exists(fixture.resolve("build/staged/native.jar")));
    }

    private Path fixture() throws Exception {
        Path fixture = Files.createDirectories(directory.resolve("binding fixture"));
        Files.writeString(fixture.resolve("settings.gradle"), "rootProject.name = 'artifact-binding-control'\n");
        Files.writeString(fixture.resolve("native.jar"), "native-default");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture.resolve("native.jar"))));
        String helperClasses = quoted(Path.of(TestModArtifacts.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        String build = """
                import org.gradle.api.file.RegularFileProperty

                buildscript { dependencies { classpath files(%s) } }
                plugins { id 'java' }

                dependencies { testImplementation files(%s) }
                def source = configurations.create('nativeArtifact')
                dependencies.add(source.name, files('native.jar'))
                def staged = layout.buildDirectory.file('staged/native.jar').get().asFile
                def resolver = new TestModArtifacts(['Test/native': TestModArtifacts.entry(staged, source, '%s')])
                def bindings = new TestModArtifactBindings(resolver, layout, providers)

                abstract class GenerateGuard extends DefaultTask {
                    @OutputFile abstract RegularFileProperty getArtifact()
                    @TaskAction void generate() {
                        def target = artifact.get().asFile
                        target.parentFile.mkdirs()
                        target.text = 'generated-override'
                    }
                }
                def generator = tasks.register('generateGuard', GenerateGuard) {
                    artifact = layout.buildDirectory.file('generated/alternate.jar')
                }
                def alternate = providers.gradleProperty('generatedOverride').isPresent()
                        ? generator.flatMap { it.artifact }
                        : layout.file(providers.gradleProperty('guardOverride').map { file(it) })
                tasks.named('test', Test) {
                    useJUnitPlatform()
                    systemProperty 'fixture.observed', file('observed.txt').absolutePath
                    bindings.bind(delegate, 'Test', 'fixture.', [nativeArtifact: 'native'], [nativeArtifact: alternate])
                }
                """.formatted(helperClasses, junitClasspath(), hash);
        Files.writeString(fixture.resolve("build.gradle"), build);
        Path test = fixture.resolve("src/test/java/BindingProbeTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, """
                import org.junit.jupiter.api.Test;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import static org.junit.jupiter.api.Assertions.assertNotNull;

                class BindingProbeTest {
                    @Test void recordDeliveredArtifact() throws Exception {
                        String argument = System.getProperty("fixture.nativeArtifact");
                        assertNotNull(argument, "The binding must deliver the selected artifact to this JVM");
                        Files.writeString(Path.of(System.getProperty("fixture.observed")), Files.readString(Path.of(argument)));
                    }
                }
                """);
        return fixture;
    }

    private BuildResult run(Path fixture, boolean failureExpected, String... arguments) {
        List<String> options = new ArrayList<>(List.of(arguments));
        options.addAll(List.of("--offline", "--console=plain", "--max-workers=1"));
        GradleRunner runner = GradleRunner.create().withProjectDir(fixture.toFile()).withArguments(options)
                                        .withTestKitDir(Path.of("build/test-kit").toAbsolutePath().toFile());
        return failureExpected ? runner.buildAndFail() : runner.build();
    }

    private static String junitClasspath() throws Exception {
        // Reuse this test worker's pinned JUnit JARs; the miniature build needs no repositories/downloads.
        var jars = new LinkedHashSet<String>();
        for (String name : List.of("org.junit.jupiter.api.Test", "org.junit.jupiter.engine.JupiterTestEngine",
                                   "org.junit.platform.engine.TestEngine", "org.junit.platform.launcher.core.LauncherFactory",
                                   "org.junit.platform.commons.JUnitException", "org.opentest4j.AssertionFailedError", "org.apiguardian.api.API")) {
            jars.add(quoted(Path.of(Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI())));
        }
        return String.join(", ", jars);
    }

    private static String quoted(Path path) {
        return "'" + path.toString().replace('\\', '/').replace("'", "\\'") + "'";
    }
}
