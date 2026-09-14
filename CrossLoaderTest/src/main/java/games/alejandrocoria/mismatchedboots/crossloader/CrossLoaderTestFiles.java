package games.alejandrocoria.mismatchedboots.crossloader;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CrossLoaderTestFiles {
    private static final String MARKERS_PROPERTY = "mismatchedboots.crossloader.markers";

    private CrossLoaderTestFiles() {
    }

    public static void write(String name, String contents) {
        Path path = markerDirectory().resolve(name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, contents);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write network acceptance marker " + path, exception);
        }
    }

    public static void failure(String endpoint, Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        write(endpoint + "-failure.txt", trace.toString());
    }

    public static String snapshot(AttributeRuleSet rules) {
        StringBuilder result = new StringBuilder("fallback=")
                .append(rules.fallback().serializedName())
                .append('\n');
        rules.rules().forEach((id, policy) -> result.append(id)
                .append('=')
                .append(policy.serializedName())
                .append('\n'));
        return result.toString();
    }

    private static Path markerDirectory() {
        String configured = System.getProperty(MARKERS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Missing system property " + MARKERS_PROPERTY);
        }
        return Path.of(configured);
    }
}
