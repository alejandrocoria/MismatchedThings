import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.List;
import java.util.Map;

/** Test-only artifact arguments. Test intent and override path interpretation belong to the caller. */
public final class TestModArtifactBindings {
    private final TestModArtifacts artifacts;
    private final ProjectLayout layout;
    private final ProviderFactory providers;

    public TestModArtifactBindings(TestModArtifacts artifacts, ProjectLayout layout, ProviderFactory providers) {
        this.artifacts = artifacts;
        this.layout = layout;
        this.providers = providers;
    }

    public void bind(Test task, String loader, String prefix, Map<String, String> bindings) {
        bind(task, loader, prefix, bindings, Map.of());
    }

    /** An absent override uses the pin; a present but missing file must fail, not fall back. */
    public void bind(Test task, String loader, String prefix, Map<String, String> bindings,
                     Map<String, ? extends Provider<RegularFile>> overrides) {
        bindings.forEach((suffix, selector) -> {
            artifacts.path(loader, selector); // Validate the selection without resolving it.
            Provider<RegularFile> fallback = layout.file(providers.provider(() -> artifacts.resolve(loader, selector)));
            Provider<RegularFile> override = overrides.get(suffix);
            Provider<RegularFile> selected = override == null ? fallback : override.orElse(fallback);
            task.getJvmArgumentProviders().add(new ArtifactArgument(prefix + suffix, selected));
        });
    }

    public static final class ArtifactArgument implements CommandLineArgumentProvider {
        private final String property;
        private final Provider<RegularFile> artifact;

        private ArtifactArgument(String property, Provider<RegularFile> artifact) {
            this.property = property;
            this.artifact = artifact;
        }

        @Input
        public String getProperty() {
            return property;
        }

        // Keeping the provider here preserves producer dependencies and fingerprints the actual chosen bytes.
        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public Provider<RegularFile> getArtifact() {
            return artifact;
        }

        @Override
        public Iterable<String> asArguments() {
            return List.of("-D" + property + "=" + artifact.get().getAsFile().getAbsolutePath());
        }
    }
}
