package games.alejandrocoria.mismatchedthings.api.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Preserves unreadable files and requires a backup before replacing malformed TOML. */
public class TomlConfigIO {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");
    private final Logger logger;

    public TomlConfigIO(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void load(Path path, DynamicConfigPart part) {
        if (Files.notExists(path)) {
            part.reset();
            save(path, part);
            return;
        }

        boolean dirty;
        try {
            dirty = part.load(read(path));
        } catch (IOException | SecurityException exception) {
            logger.warn("Cannot read config file {}. Using in-memory defaults without changing the file.",
                               path, exception);
            part.reset();
            return;
        } catch (ParsingException exception) {
            logger.warn("Config file {} is malformed. Using defaults.", path, exception);
            part.reset();
            try {
                Path backup = backup(path);
                logger.warn("Copied malformed config file {} to {}", path, backup);
            } catch (IOException | SecurityException backupFailure) {
                logger.warn("Cannot back up config file {}. Using in-memory defaults without changing the file.",
                                   path, backupFailure);
                return;
            }
            // Only a successfully preserved malformed file may be replaced with defaults.
            save(path, part);
            return;
        }

        if (dirty) {
            save(path, part);
        }
    }

    private void save(Path path, DynamicConfigPart part) {
        try (CommentedFileConfig config = open(path)) {
            config.clear();
            part.save(config);
            config.save();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save config " + path, exception);
        }
    }

    protected CommentedConfig read(Path path) throws IOException {
        // Read first so parser errors cannot disguise filesystem failures as malformed TOML.
        return new TomlParser().parse(Files.readString(path));
    }

    protected Path backup(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        String baseName = path.getFileName() + "." + BACKUP_TIMESTAMP.format(LocalDateTime.now());

        Path backup = parent.resolve(baseName + ".bak");
        int suffix = 2;
        while (Files.exists(backup)) {
            backup = parent.resolve(baseName + "-" + suffix++ + ".bak");
        }

        // Never replace an existing backup, even if another writer won the suffix race.
        return Files.copy(path, backup);
    }

    protected CommentedFileConfig open(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build();
    }
}
