package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class AttributeRulesConfig {
    public static final String FILE_NAME = Constants.MOD_ID + "-attributes.toml";
    private final Path path;
    private final TomlConfigIO files;
    private final AttributeRulesConfigPart attributes = new AttributeRulesConfigPart();

    public AttributeRulesConfig(Path path) {
        this(path, new TomlConfigIO());
    }

    AttributeRulesConfig(Path path, TomlConfigIO files) {
        this.path = Objects.requireNonNull(path, "path");
        this.files = Objects.requireNonNull(files, "files");
    }

    public AttributeRuleSet load() {
        if (Files.notExists(path)) {
            attributes.reset();
            save();
            return attributes.resolved();
        }

        boolean dirty;
        try {
            dirty = attributes.load(files.read(path));
        } catch (IOException | SecurityException exception) {
            Constants.LOG.warn("Cannot read config file {}. Using in-memory defaults without changing the file.", path, exception);
            attributes.reset();
            return AttributeRuleDefaults.ruleSet();
        } catch (ParsingException exception) {
            Constants.LOG.warn("Config file {} is malformed. Using defaults.", path, exception);
            attributes.reset();
            try {
                Path backup = files.backup(path);
                Constants.LOG.warn("Copied malformed config file {} to {}", path, backup);
            } catch (IOException | SecurityException backupFailure) {
                Constants.LOG.warn("Cannot back up config file {}. Using in-memory defaults without changing the file.",
                        path, backupFailure);
                return AttributeRuleDefaults.ruleSet();
            }
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
        try (CommentedFileConfig config = files.open(path)) {
            config.clear();
            attributes.save(config);
            config.save();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save MismatchedBoots config " + path, exception);
        }
    }
}
