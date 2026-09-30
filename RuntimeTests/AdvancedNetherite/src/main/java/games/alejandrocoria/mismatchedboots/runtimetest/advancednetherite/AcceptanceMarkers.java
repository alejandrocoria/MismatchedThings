package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AcceptanceMarkers {
    private static final String MARKERS_PROPERTY = "mismatchedboots.runtimetest.markers";

    private AcceptanceMarkers() {
    }

    public static void write(String name, String contents) {
        String configured = System.getProperty(MARKERS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Missing system property " + MARKERS_PROPERTY);
        }
        Path path = Path.of(configured).resolve(name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, contents);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write acceptance marker " + path, exception);
        }
    }

    public static void failure(Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        write("advanced-netherite-failure.txt", trace.toString());
    }
}
