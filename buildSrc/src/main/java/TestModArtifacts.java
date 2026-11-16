import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pinned test/development downloads. Never reads or writes the owner's indev instances. */
public final class TestModArtifacts {
    public record Artifact(File target, Configuration configuration, String sha256,
                           List<String> requires, List<String> clientRequires) {
        public Artifact {
            requires = List.copyOf(requires);
            clientRequires = List.copyOf(clientRequires);
        }

        public Artifact(File target, Configuration configuration, String sha256) {
            this(target, configuration, sha256, List.of(), List.of());
        }
    }

    private final Map<String, Artifact> artifacts;

    public TestModArtifacts(Map<String, Artifact> artifacts) {
        this.artifacts = Map.copyOf(artifacts);
        // Validate the union of side edges without resolving any Gradle configuration.
        for (String key : artifacts.keySet()) {
            int separator = key.indexOf('/');
            if (separator <= 0 || separator == key.length() - 1) {
                throw new GradleException("Invalid test mod key " + key);
            }
            runtimeSelection(key.substring(0, separator), "client", List.of(key.substring(separator + 1)));
        }
    }

    public static Artifact entry(File target, Configuration configuration, String sha256) {
        return new Artifact(target, configuration, sha256);
    }

    /** JSON arrays are checked here so malformed fields fail before any downloads. */
    public static Artifact entry(File target, Configuration configuration, String sha256, Object requires, Object clientRequires) {
        return new Artifact(target, configuration, sha256,
                            dependencies(target, "requires", requires), dependencies(target, "clientRequires", clientRequires));
    }

    public File path(String loader, String selector) {
        return artifact(loader, selector).target();
    }

    /** Pinned metadata for fixtures that also verify staged files outside the resolver. */
    public String sha256(String loader, String selector) {
        return artifact(loader, selector).sha256();
    }

    /** Direct selection: aliases are deduplicated, dependency edges are not followed. */
    public List<File> paths(String loader, Collection<String> selectors) {
        return directSelection(loader, selectors).stream().map(Artifact::target).toList();
    }

    public List<File> resolveAll(String loader, Collection<String> selectors) {
        return directSelection(loader, selectors).stream().map(entry -> resolve(loader, entry)).toList();
    }

    /** Runtime selection follows only the audited edges for the explicitly selected roots/side. */
    public List<File> runtimePaths(String loader, String side, Collection<String> roots) {
        return runtimeSelection(loader, side, roots).stream().map(Artifact::target).toList();
    }

    public List<File> resolveRuntime(String loader, String side, Collection<String> roots) {
        return runtimeSelection(loader, side, roots).stream().map(entry -> resolve(loader, entry)).toList();
    }

    public File resolve(String loader, String selector) {
        return resolve(loader, artifact(loader, selector));
    }

    /** Verify an explicitly supplied official pin in place. Never repairs or replaces that file. */
    public File verify(String loader, String selector, File supplied) {
        Artifact artifact = artifact(loader, selector);
        try {
            verifyFile(loader, artifact, supplied);
            return supplied;
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new GradleException("Cannot verify test mod " + loader + "/" + selector + " at " + supplied, failure);
        }
    }

    private File resolve(String loader, Artifact artifact) {
        try {
            File target = artifact.target();
            if (target.isFile() && hash(target).equals(artifact.sha256())) {
                return target;
            }

            File source = artifact.configuration().getSingleFile();
            verifyFile(loader, artifact, source);

            Files.createDirectories(target.toPath().getParent());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new GradleException("Cannot prepare test mod " + loader + "/" + artifact.target().getName(), failure);
        }
    }

    private Artifact artifact(String loader, String selector) {
        Artifact artifact = artifacts.get(loader + "/" + selector);
        if (artifact == null) {
            throw new GradleException("Unregistered test mod " + loader + "/" + selector);
        }
        return artifact;
    }

    private Set<Artifact> directSelection(String loader, Collection<String> selectors) {
        Set<Artifact> selected = new LinkedHashSet<>();
        for (String selector : selectors) {
            selected.add(artifact(loader, selector));
        }
        return selected;
    }

    private Set<Artifact> runtimeSelection(String loader, String side, Collection<String> roots) {
        if (!side.equals("client") && !side.equals("server")) {
            throw new GradleException("Unknown test mod side " + side);
        }
        Set<Artifact> selected = new LinkedHashSet<>();
        Set<Artifact> visiting = new LinkedHashSet<>();
        for (String root : roots) {
            visit(loader, root, side.equals("client"), selected, visiting);
        }
        return selected;
    }

    private void visit(String loader, String selector, boolean client, Set<Artifact> selected, Set<Artifact> visiting) {
        Artifact entry = artifact(loader, selector);
        if (visiting.contains(entry)) {
            throw new GradleException("Test mod dependency cycle at " + loader + "/" + selector);
        }
        if (!selected.add(entry)) {
            return;
        }
        visiting.add(entry);
        for (String dependency : entry.requires()) {
            visit(loader, dependency, client, selected, visiting);
        }
        if (client) {
            for (String dependency : entry.clientRequires()) {
                visit(loader, dependency, true, selected, visiting);
            }
        }
        visiting.remove(entry);
    }

    private static List<String> dependencies(File target, String field, Object value) {
        if (!(value instanceof List<?> list)) {
            throw new GradleException("Invalid " + field + " for test mod " + target.getName() + ": expected an array of selectors");
        }
        List<String> result = new ArrayList<>();
        for (Object dependency : list) {
            if (!(dependency instanceof String selector) || selector.isBlank() || selector.contains("/")) {
                throw new GradleException("Invalid " + field + " selector for test mod " + target.getName() + ": " + dependency);
            }
            result.add(selector);
        }
        return List.copyOf(result);
    }

    private static void verifyFile(String loader, Artifact artifact, File supplied) throws IOException, NoSuchAlgorithmException {
        if (!supplied.isFile()) {
            throw new GradleException("Missing pinned test mod " + loader + "/" + artifact.target().getName() + " at " + supplied);
        }
        if (!hash(supplied).equals(artifact.sha256())) {
            throw new GradleException("Unexpected SHA-256 for test mod " + loader + "/" + artifact.target().getName() + " at " + supplied);
        }
    }

    private static String hash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
