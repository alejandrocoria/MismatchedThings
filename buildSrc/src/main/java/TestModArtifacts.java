import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Test-only pinned downloads. Never reads or writes the owner's indev instances. */
public final class TestModArtifacts {
    public record Artifact(File target, Configuration configuration, String sha256) {}

    private final Map<String, Artifact> artifacts;

    public TestModArtifacts(Map<String, Artifact> artifacts) {
        this.artifacts = Map.copyOf(artifacts);
    }

    public static Artifact entry(File target, Configuration configuration, String sha256) {
        return new Artifact(target, configuration, sha256);
    }

    public File path(String loader, String filename) {
        return artifact(loader, filename).target();
    }

    public File resolve(String loader, String filename) {
        Artifact artifact = artifact(loader, filename);
        try {
            File target = artifact.target();
            if (target.isFile() && hash(target).equals(artifact.sha256())) {
                return target;
            }

            File source = artifact.configuration().getSingleFile();
            if (!hash(source).equals(artifact.sha256())) {
                throw new GradleException("Unexpected SHA-256 for test mod " + loader + "/" + filename);
            }

            Files.createDirectories(target.toPath().getParent());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new GradleException("Cannot prepare test mod " + loader + "/" + filename, failure);
        }
    }

    private Artifact artifact(String loader, String filename) {
        Artifact artifact = artifacts.get(loader + "/" + filename);
        if (artifact == null) {
            throw new GradleException("Unregistered test mod " + loader + "/" + filename);
        }
        return artifact;
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
