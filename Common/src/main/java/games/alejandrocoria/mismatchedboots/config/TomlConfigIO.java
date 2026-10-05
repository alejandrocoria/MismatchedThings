package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

class TomlConfigIO {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");

    CommentedConfig read(Path path) throws IOException {
        // Read first so parser errors cannot disguise filesystem failures as malformed TOML.
        return new TomlParser().parse(Files.readString(path));
    }

    Path backup(Path path) throws IOException {
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

    CommentedFileConfig open(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build();
    }
}
