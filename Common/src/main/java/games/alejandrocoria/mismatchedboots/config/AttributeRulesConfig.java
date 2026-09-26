package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class AttributeRulesConfig {
    public static final String FILE_NAME = Constants.MOD_ID + "-attributes.toml";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");

    private final Path path;
    private final AttributeRulesConfigPart attributes = new AttributeRulesConfigPart();

    public AttributeRulesConfig(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public AttributeRuleSet load() {
        if (!Files.exists(path)) {
            attributes.reset();
            save();
            return attributes.resolved();
        }

        boolean dirty;
        try (CommentedFileConfig config = TomlConfigIO.open(path)) {
            config.load();
            dirty = attributes.load(config);
        } catch (Exception exception) {
            Constants.LOG.warn("Config file {} is malformed or unreadable. Resetting to defaults.", path, exception);
            createBackup();
            attributes.reset();
            save();
            return AttributeRuleDefaults.ruleSet();
        }
        if (dirty) {
            save();
        }
        return attributes.resolved();
    }

    public Path path() {
        return path;
    }

    private void save() {
        try (CommentedFileConfig config = TomlConfigIO.open(path)) {
            config.clear();
            attributes.save(config);
            config.save();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save MismatchedBoots config " + path, exception);
        }
    }

    private void createBackup() {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path backup = nextBackupPath(parent);
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Constants.LOG.warn("Copied malformed config file {} to {}", path, backup);
        } catch (IOException exception) {
            Constants.LOG.warn("Failed to create a backup for malformed config file {}", path, exception);
        }
    }

    private Path nextBackupPath(Path parent) {
        String baseName = path.getFileName() + "." + BACKUP_TIMESTAMP.format(LocalDateTime.now());
        Path backup = parent.resolve(baseName + ".bak");
        int suffix = 2;
        while (Files.exists(backup)) {
            backup = parent.resolve(baseName + "-" + suffix + ".bak");
            suffix++;
        }
        return backup;
    }
}
