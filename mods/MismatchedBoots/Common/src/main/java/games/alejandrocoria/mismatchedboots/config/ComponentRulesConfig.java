package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;

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
        files.load(path, rules);
        return rules.resolved();
    }

    public Path path() {
        return path;
    }

}
