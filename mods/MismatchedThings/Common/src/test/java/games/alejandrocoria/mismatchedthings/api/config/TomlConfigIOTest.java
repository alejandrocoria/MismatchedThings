package games.alejandrocoria.mismatchedthings.api.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomlConfigIOTest {
    @TempDir
    Path directory;

    @Test
    void createsIndependentConsumerFilesAndRoundTripsTheirOwnEnumValues() throws IOException {
        var files = new TomlConfigIO(LoggerFactory.getLogger("test-consumer"));
        Path firstPath = directory.resolve("first.toml");
        Path secondPath = directory.resolve("second.toml");
        var first = new TestPart();
        var second = new TestPart();
        files.load(firstPath, first);
        files.load(secondPath, second);
        assertTrue(Files.readString(firstPath).contains("Consumer setting"));

        Files.writeString(firstPath, "mode = \"CUSTOM\"\n");
        byte[] original = Files.readAllBytes(firstPath);
        files.load(firstPath, first);

        assertEquals(Mode.CUSTOM, first.entry.get());
        assertEquals(Mode.DEFAULT, second.entry.get());
        assertArrayEquals(original, Files.readAllBytes(firstPath));
        assertTrue(Files.readString(secondPath).contains("DEFAULT"));
    }

    @Test
    void normalizesInvalidValuesAndBacksUpMalformedFilesWithoutReplacingOlderBackups() throws IOException {
        var files = new TomlConfigIO(LoggerFactory.getLogger("test-consumer"));
        Path path = directory.resolve("consumer.toml");
        var part = new TestPart();
        Files.writeString(path, "mode = 10\n");
        files.load(path, part);
        assertEquals(Mode.DEFAULT, part.entry.get());
        assertTrue(Files.readString(path).contains("DEFAULT"));

        byte[] malformed = "[broken\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, malformed);
        Path previous = files.backup(path);
        files.load(path, part);

        assertArrayEquals(malformed, Files.readAllBytes(previous));
        try (var entries = Files.list(directory)) {
            var backups = entries.filter(file -> file.toString().endsWith(".bak")).toList();
            assertEquals(2, backups.size());
            for (Path backup : backups) {
                assertArrayEquals(malformed, Files.readAllBytes(backup));
            }
        }
        assertTrue(Files.readString(path).contains("DEFAULT"));
    }

    @Test
    void failedBackupResetsLiveValuesWithoutRewritingTheFile() throws IOException {
        Path path = directory.resolve("consumer.toml");
        var part = new TestPart();
        var files = new TomlConfigIO(LoggerFactory.getLogger("test-consumer")) {
            @Override
            protected Path backup(Path target) throws IOException {
                throw new IOException("Injected backup failure");
            }
        };
        Files.writeString(path, "mode = \"CUSTOM\"\n");
        files.load(path, part);
        assertEquals(Mode.CUSTOM, part.entry.get());
        byte[] malformed = "[broken\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, malformed);

        files.load(path, part);

        assertEquals(Mode.DEFAULT, part.entry.get());
        assertArrayEquals(malformed, Files.readAllBytes(path));
    }

    private enum Mode {
        DEFAULT,
        CUSTOM;

        static Optional<Mode> parse(String value) {
            return Arrays.stream(values()).filter(mode -> mode.name().equals(value)).findFirst();
        }
    }

    private static final class TestPart implements DynamicConfigPart {
        private final EnumConfigEntry<Mode> entry = new EnumConfigEntry<>(
                "mode", Mode.DEFAULT, "Consumer setting", Mode::parse, Mode::name);

        @Override
        public boolean load(CommentedConfig config) {
            return entry.load(config);
        }

        @Override
        public void save(CommentedConfig config) {
            entry.save(config);
        }

        @Override
        public void reset() {
            entry.reset();
        }
    }
}
