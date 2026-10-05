package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ComponentRulesConfig {
    public static final String FILE_NAME = Constants.MOD_ID + "-components.toml";
    private final Path path;
    private final TomlConfigIO files;
    private final ComponentRulesConfigPart rules = new ComponentRulesConfigPart();

    public ComponentRulesConfig(Path path) {
        this(path, new TomlConfigIO());
    }

    ComponentRulesConfig(Path path, TomlConfigIO files) {
        this.path = Objects.requireNonNull(path, "path");
        this.files = Objects.requireNonNull(files, "files");
    }

    public ComponentRules load() {
        if (Files.notExists(path)) {
            rules.reset();
            save();
            return rules.resolved();
        }

        boolean dirty;
        try {
            dirty = rules.load(files.read(path));
        } catch (IOException | SecurityException exception) {
            Constants.LOG.warn("Cannot read config file {}. Using in-memory defaults without changing the file.", path, exception);
            rules.reset();
            return ComponentRuleDefaults.state();
        } catch (ParsingException exception) {
            Constants.LOG.warn("Config file {} is malformed. Using defaults.", path, exception);
            rules.reset();
            try {
                Path backup = files.backup(path);
                Constants.LOG.warn("Copied malformed config file {} to {}", path, backup);
            } catch (IOException | SecurityException backupFailure) {
                Constants.LOG.warn("Cannot back up config file {}. Using in-memory defaults without changing the file.",
                        path, backupFailure);
                return ComponentRuleDefaults.state();
            }
            save();
            return ComponentRuleDefaults.state();
        }
        if (dirty) {
            save();
        }
        return rules.resolved();
    }

    public Path path() {
        return path;
    }

    private void save() {
        try (CommentedFileConfig config = files.open(path)) {
            config.clear();
            rules.save(config);
            config.save();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save MismatchedBoots component config " + path, exception);
        }
    }
}
